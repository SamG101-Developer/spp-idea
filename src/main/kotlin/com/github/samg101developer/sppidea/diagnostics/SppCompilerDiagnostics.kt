package com.github.samg101developer.sppidea.diagnostics

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
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

// What one run of the compiler answered: the errors, by the
// file they point at, and the names it resolved, by file.
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

  // Files a run has been started for and not yet finished, so
  // asking twice does not compile twice.
  private val warming = ConcurrentHashMap.newKeySet<String>()

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

  /**
   * What is already known about the names in [file], and nothing more: hover and go-to-definition are asked on the
   * editor's thread, where neither starting a compile nor waiting on one that is running is acceptable - the
   * editor would sit frozen for as long as it took. The annotator is what fills this in, so the answer is there a
   * moment after a file has been highlighted, and stays there once it has.
   */
  fun cachedSymbolsFor(file: Path): List<SppSymbol> = symbolsByFile[file.toString()].orEmpty()

  /** What the named type or namespace holds, for the list offered after a "." or a "::". */
  fun cachedMembersOf(owner: String): List<SppMember> = membersByOwner[owner]?.members.orEmpty()

  /** The calls written in [file], for the names offered between their brackets. */
  fun cachedSignaturesFor(file: Path): List<SppSignature> = signaturesByFile[file.toString()].orEmpty()

  /** What each part of [file] can name, for the names offered where nothing is being accessed. */
  fun cachedScopesFor(file: Path): List<SppScope> = scopesByFile[file.toString()].orEmpty()

  /** What each "cmp" written in [file] computed, for showing the answer beside the declaration. */
  fun cachedComptimeValuesFor(file: Path): List<SppComptimeValue> = valuesByFile[file.toString()].orEmpty()

  /** Whether a compile is running right now, whatever it was asked about. */
  fun isAnalysing(): Boolean = analysing

  /** Ask the editor to work out again what it shows: the errors on a file, and the gutter marks beside it. */
  private fun refreshEditor() {
    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed) {
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
    }
  }

  /** Whether [file] has been analysed at all, which is a different question from whether it held any names. */
  fun isAnalysed(file: Path): Boolean = symbolsByFile.containsKey(file.toString())

  /** What the last run said about [file], for the gutter to mark which functions did not get that far. */
  fun cachedDiagnosticsFor(file: Path): List<SppDiagnostic> = cached.diagnostics[file.toString()].orEmpty()

  /**
   * Work out what the names in [file] mean, in the background, so that asking about them shortly will be answered.
   *
   * Hover and go-to-definition can only use what is already known, and what fills that in is the annotator's run -
   * which happens when a file is highlighted, and for that file alone. Without this, a file just opened, or just
   * returned to, answers nothing until the next highlighting pass, which reads as navigation working only every
   * few attempts.
   */
  fun warmUp(file: VirtualFile) {
    val path = runCatching { file.toNioPath().absolute() }.getOrNull() ?: return
    if (symbolsByFile.containsKey(path.toString())) {
      return
    }

    val root = projectRootFor(path) ?: return
    val executable = resolveSppExecutable() ?: return

    // One compile analyses every module there is, so a file the project itself holds is indexed along with all of
    // its siblings rather than on its own - opening the next file then costs nothing. A file from a dependency is
    // not in that sweep, and is worth indexing by itself when someone is actually looking at it.
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

  /** Whether the file is the project's own, rather than one of the dependencies it keeps under "vcs". */
  private fun isProjectFile(root: Path, file: Path): Boolean =
    file.startsWith(root) && !root.relativize(file).toString().startsWith("vcs")

  private fun run(root: String, executable: String, indexFile: Path?): SppAnalysis {
    // No pty, unlike the run configuration: the compiler's progress bars check whether stdout is a terminal and
    // leave a pipe alone, which is exactly what is wanted here - the only thing on stdout is then the json.
    val commandLine = GeneralCommandLine(executable)
      .withParameters("build", "-m", "dev", "--analyse-only", "--skip-vcs", "--message-format=json")
      .withParameters(
        // Indexing the whole project costs the same compile as indexing one file of it, so that is the
        // default; a single file is asked for only when it is not one the project holds.
        if (indexFile == null) listOf("--index-project") else listOf("--index-file", indexFile.toString()),
      )
      .withWorkDirectory(root)
      .withCharset(Charsets.UTF_8)

    val output = try {
      CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MS, true)
    } catch (e: Exception) {
      thisLogger().warn("Could not run '${commandLine.commandLineString}'", e)
      return SppAnalysis(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }

    if (output.isTimeout) {
      thisLogger().warn("'spp build --analyse-only' timed out after ${TIMEOUT_MS}ms")
      return SppAnalysis(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }

    // Errors are the only thing reported, so an exit code of zero means an empty map rather than no answer.
    val diagnostics = SppDiagnosticParser.parse(output.stdout)
      .flatMap { diagnostic -> diagnostic.labels.map { it.file to diagnostic } }
      .distinct()
      .groupBy({ it.first }, { it.second })
    val symbols = SppDiagnosticParser.parseSymbols(output.stdout).groupBy { it.use.file }
    val members = SppDiagnosticParser.parseMembers(output.stdout).associateBy { it.owner }
    val signatures = SppDiagnosticParser.parseSignatures(output.stdout).groupBy { it.arguments.file }
    val scopes = SppDiagnosticParser.parseScopes(output.stdout).groupBy { it.where.file }
    val values = SppDiagnosticParser.parseComptimeValues(output.stdout).groupBy { it.where.file }
    return SppAnalysis(diagnostics, symbols, members, signatures, scopes, values)
  }

  companion object {
    private const val MIN_INTERVAL_MS = 2_000L
    private const val TIMEOUT_MS = 120_000

    fun getInstance(project: Project): SppCompilerDiagnostics = project.getService(SppCompilerDiagnostics::class.java)

    /**
     * The project directory a file belongs to: the nearest ancestor holding an `spp.toml`, which is what the
     * compiler itself resolves a project from and what it has to be run in.
     */
    fun projectRootFor(file: Path): Path? = generateSequence(file.parent) { it.parent }
      .firstOrNull { it.resolve("spp.toml").isRegularFile() }
  }
}
