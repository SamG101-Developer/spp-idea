package com.github.samg101developer.sppidea.diagnostics

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiModificationTracker
import java.nio.file.Path
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.absolute
import kotlin.io.path.isRegularFile

// What one run of the compiler produced: the errors by the
// file they point at, and the names it resolved by file. All
// split into their variant maps depending on what they are
// and what they are used for, so the editor can request what's
// needed.
data class SppAnalysis(
  val diagnostics: Map<String, List<SppDiagnostic>>,
  val symbols: Map<String, List<SppSymbol>>,
  val members: Map<String, SppMemberList>,
  val signatures: Map<String, List<SppSignature>>,
  val scopes: Map<String, List<SppScope>>,
  val comptimeValues: Map<String, List<SppComptimeValue>>,
)

// The service that runs the s++ compiler for its diagnostics,
// and the meaning of the names in a file, caching the results.
// Until a true language server has been built in s++, this is
// the slow, but only way, to get the information the editor
// needs to show errors and offer navigation.
@Service(Service.Level.PROJECT)
class SppCompilerDiagnostics(private val project: Project) {

  private val lock = ReentrantLock()

  /// Volatile, like the symbols below, because the gutter reads
  // it while a run may be holding the lock.
  @Volatile
  private var cached: SppAnalysis = SppAnalysis(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
  private var cachedStamp: Long = -1
  private var cachedKey: String? = null
  private var lastRunAt: Long = 0

  // What every run so far worked out about the names in each
  // file it indexed, kept apart from "cached" because it's
  // read from the editor's thread, and it accumulates rather
  // than replacing.
  @Volatile
  private var symbolsByFile: Map<String, List<SppSymbol>> = emptyMap()

  // What each type and namespace holds, kept the same way
  // and for the same reason as the symbols above.
  @Volatile
  private var membersByOwner: Map<String, SppMemberList> = emptyMap()

  // The calls in each file, and what each part of each file
  // can name. Kept the same way, and read on the editor's
  // thread by completion.
  @Volatile
  private var signaturesByFile: Map<String, List<SppSignature>> = emptyMap()

  // The scopes in each file, and what each part of each file
  // can name. Kept the same way, and read on the editor's
  // thread by completion.
  @Volatile
  private var scopesByFile: Map<String, List<SppScope>> = emptyMap()

  // The "cmp" calls in each file, and what each part of each
  // file computed. Kept the same way, and read on the editor's
  // thread by the line painter.
  @Volatile
  private var valuesByFile: Map<String, List<SppComptimeValue>> = emptyMap()

  // The files the project tree underlines, with their folders,
  // for holding an error.
  private val problemFiles = SppProblemFiles(project)

  // Files a run has been started for and not yet finished, so
  // asking twice does not compile twice.
  private val warming = ConcurrentHashMap.newKeySet<String>()

  // The last failure reported, so the same one is not reported
  // again on every run.
  @Volatile
  private var lastFailure: String? = null

  // Whether a compilation is under way, which the gutter shows
  // so that "nothing found yet" is visibly temporary.
  @Volatile
  private var analysing = false

  // Every diagnostic the project has, by the file it points
  // at. Returns the previous answer when nothing has changed
  // or when one run has only just finished.
  fun analyse(root: Path, executable: String, indexFile: Path? = null): SppAnalysis {
    val stamp = PsiModificationTracker.getInstance(project).modificationCount
    val key = "$root|${indexFile ?: "project"}"

    lock.withLock {
      val unchanged = stamp == cachedStamp && key == cachedKey
      val tooSoon = System.currentTimeMillis() - lastRunAt < MIN_INTERVAL_MS
      if (unchanged || (tooSoon && key == cachedKey)) return cached

      // The gutter says which files are known and which are being
      // worked out, so it is asked to redraw as the run starts as
      // well as when it ends - otherwise the mark only ever changes
      // once the answer is in.
      analysing = true
      refreshEditor()
      cached = try {
        run(root.toString(), executable, indexFile)
      } finally {
        analysing = false
      }

      // A file that was indexed is recorded even when it held no
      // names worth recording, so that "analysed, nothing there"
      // is remembered as an answer rather than looking like a file
      // nobody has looked at yet.
      val fresh = cached.symbols.toMutableMap()
      indexFile?.let { fresh.putIfAbsent(it.toString(), emptyList()) }
      symbolsByFile = symbolsByFile + fresh
      membersByOwner = membersByOwner + cached.members
      signaturesByFile = signaturesByFile + cached.signatures
      scopesByFile = scopesByFile + cached.scopes
      valuesByFile = valuesByFile + cached.comptimeValues
      problemFiles.update(cached.diagnostics)
      cachedStamp = stamp
      cachedKey = key
      lastRunAt = System.currentTimeMillis()

      // What the editor shows about a file - the errors on it,
      // and the gutter saying which of its functions are indexed
      // - was worked out before this run finished, so ask for it
      // to be worked out again.
      refreshEditor()
      return cached
    }
  }

  // Get all the names the last run worked out for [file], for
  // navigation and hover to use.
  fun cachedSymbolsFor(file: Path): List<SppSymbol> = symbolsByFile[file.toString()].orEmpty()

  // Get all the members the last run worked out for [owner],
  // for completion to use. Used for completion following "."
  // or "::".
  fun cachedMembersOf(owner: String): List<SppMember> = membersByOwner[owner]?.members.orEmpty()

  // Get all the calls the last run worked out for [file], for
  // completion to use. Used for completion between brackets.
  fun cachedSignaturesFor(file: Path): List<SppSignature> = signaturesByFile[file.toString()].orEmpty()

  // Get all the scopes the last run worked out for [file],
  // for completion to use. Used for completion with no member
  // access (ie what's in this function scope that can used).
  fun cachedScopesFor(file: Path): List<SppScope> = scopesByFile[file.toString()].orEmpty()

  // Get all the comptime values the last run worked out for
  // [file], for the line painter to use. Used for showing the
  // answer beside the declaration.
  fun cachedComptimeValuesFor(file: Path): List<SppComptimeValue> = valuesByFile[file.toString()].orEmpty()

  // Whether a compilation is running right now, whatever it was
  // asked about.
  fun isAnalysing(): Boolean = analysing

  // Get the editor to re-highlight, so that the gutter marks
  // beside functions and the errors on a file are worked out
  // again.
  private fun refreshEditor() {
    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed) {
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
    }
  }

  // Whether [file] has been analysed by the last run, so that
  // the gutter can mark which functions are known and which are
  // still being worked out.
  fun isAnalysed(file: Path): Boolean = symbolsByFile.containsKey(file.toString())

  // What the last run said about [file], for the gutter to
  // mark which functions did not get that far.
  fun cachedDiagnosticsFor(file: Path): List<SppDiagnostic> = cached.diagnostics[file.toString()].orEmpty()

  // Warm a cache which answers what the names in [file] mean,
  // so that navigation and hover can use it. A file the project
  // itself holds is indexed along with all of its siblings,
  // so opening the next file costs nothing. A file from a
  // dependency is not in that sweep, and is worth indexing by
  // itself when someone is actually looking at it.
  fun warmUp(file: VirtualFile) {
    val path = runCatching { file.toNioPath().absolute() }.getOrNull() ?: return
    if (symbolsByFile.containsKey(path.toString())) {
      return
    }

    val root = projectRootFor(path) ?: return
    val executable = resolveSppExecutable() ?: return

    // One compile analyses every module there is, so a file the
    // project itself holds is indexed along with all of its
    // siblings rather than on its own - opening the next file
    // then costs nothing. A file from a dependency is not in
    // that sweep, and is worth indexing by itself when someone
    // is actually looking at it.
    val target = if (isProjectFile(root, path)) null else path
    val key = "$root|${target ?: "project"}"
    if (!warming.add(key)) {
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        analyse(root, executable, target)
      } finally {
        warming.remove(key)
      }
    }
  }

  // Whether the file is the project's own, rather than one of
  // the dependencies it keeps under "vcs".
  private fun isProjectFile(root: Path, file: Path): Boolean =
    file.startsWith(root) && !root.relativize(file).toString().startsWith("vcs")

  private fun run(root: String, executable: String, indexFile: Path?): SppAnalysis {
    // The compiler may see the project under other paths than
    // the IDE does (a Windows IDE with the project in WSL), so
    // paths are translated on the way in and on the way out.
    val paths = SppPathMapping.forRoot(Path.of(root))

    // Invoke the analysis compilation (a general compilation
    // using "spp build", but with some fine-tuning flags to
    // make it faster and extract what we need in JSON.
    val commandLine = GeneralCommandLine(executable)
      .withParameters("build", "-m", "dev", "--analyse-only", "--skip-vcs", "--message-format=json")
      .withParameters(
        if (indexFile == null) listOf("--index-project") else listOf("--index-file", paths.toCompiler(indexFile))
      )
      .withWorkDirectory(root)
      .withCharset(Charsets.UTF_8)

    // Get the output from the command, which is the JSON info
    // that will get parsed into the maps the editor needs.
    val output = try {
      CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MS, true)
    } catch (e: Exception) {
      thisLogger().warn("Could not run '${commandLine.commandLineString}'", e)
      reportFailure("Could not run the S++ compiler at $executable: ${e.message ?: e}")
      return EMPTY
    }

    // Handle a timeout (if the compiler is taking too long to
    // run).
    if (output.isTimeout) {
      thisLogger().warn("'spp build --analyse-only' timed out after ${TIMEOUT_MS}ms")
      reportFailure("The S++ compiler did not finish analysing $root within ${TIMEOUT_MS / 1000}s.")
      return EMPTY
    }

    // A failed exit with JSON is just a project with errors in
    // it. One with no JSON at all means the compiler could not
    // analyse anything, and stderr says why.
    if (output.exitCode != 0 && output.stdout.lineSequence().none { it.trimStart().startsWith("{") }) {
      val reason = output.stderr.trim().lines().takeLast(10).joinToString("\n")
      thisLogger().warn("'spp build --analyse-only' in $root exited with ${output.exitCode}: $reason")
      reportFailure("The S++ compiler exited with code ${output.exitCode} in $root.\n$reason")
      return EMPTY
    }
    lastFailure = null

    // Errors are the only thing reported, so an exit code of
    // zero means an empty map rather than no answer.
    val toIde = paths::toIde
    val diagnostics = SppDiagnosticParser.parse(output.stdout, toIde)
      .flatMap { diagnostic -> diagnostic.labels.map { it.file to diagnostic } }
      .distinct()
      .groupBy({ it.first }, { it.second })
    val symbols = SppDiagnosticParser.parseSymbols(output.stdout, toIde).groupBy { it.use.file }
    val members = SppDiagnosticParser.parseMembers(output.stdout, toIde).associateBy { it.owner }
    val signatures = SppDiagnosticParser.parseSignatures(output.stdout, toIde).groupBy { it.arguments.file }
    val scopes = SppDiagnosticParser.parseScopes(output.stdout, toIde).groupBy { it.where.file }
    val values = SppDiagnosticParser.parseComptimeValues(output.stdout, toIde).groupBy { it.where.file }
    return SppAnalysis(diagnostics, symbols, members, signatures, scopes, values)
  }

  // Say once that analysis is failing, rather than on every one
  // of the runs that keep failing the same way, which would bury
  // the editor in balloons. A run that works resets it.
  private fun reportFailure(message: String) {
    if (message == lastFailure) return
    lastFailure = message
    NotificationGroupManager.getInstance()
      .getNotificationGroup("S++")
      .createNotification("S++ analysis failed", message, NotificationType.WARNING)
      .notify(project)
  }

  companion object {
    private const val MIN_INTERVAL_MS = 2_000L
    private const val TIMEOUT_MS = 120_000
    private val EMPTY = SppAnalysis(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

    fun getInstance(project: Project): SppCompilerDiagnostics = project.getService(SppCompilerDiagnostics::class.java)

    // The project directory a file belongs to: the nearest
    // ancestor holding an `spp.toml`, which is what the compiler
    // itself resolves a project from and what it has to be run
    // in.
    fun projectRootFor(file: Path): Path? = generateSequence(file.parent) { it.parent }
      .firstOrNull { it.resolve("spp.toml").isRegularFile() }
  }
}
