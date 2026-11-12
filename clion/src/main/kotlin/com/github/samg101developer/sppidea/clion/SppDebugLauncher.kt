package com.github.samg101developer.sppidea.clion

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.execution.ParametersListUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.jetbrains.cidr.cpp.execution.debugger.backend.CLionGDBDriverConfiguration
import com.jetbrains.cidr.cpp.execution.debugger.backend.CLionLLDBDriverConfiguration
import com.jetbrains.cidr.cpp.toolchains.CPPDebugger
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.execution.CidrLauncher
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.runOnEDT
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

// Starts an S++ program under CLion's native debugger. The
// program is built first ("spp build", by SppCidrDebugRunner),
// which also sets "executable" ("spp info executable");
// this then starts it in the module's directory, with the
// configuration's program arguments, under the debugger of the
// default toolchain (GDB or LLDB).
class SppDebugLauncher(
  private val project: Project,
  private val workingDir: String,
  private val programArguments: String?,
) : CidrLauncher() {

  // The program to start, as "spp" printed it.
  @Volatile
  var executable: String? = null

  override fun getProject(): Project = project

  private fun programCommandLine(): GeneralCommandLine {
    val path = executable ?: throw ExecutionException("The program was not built, so there is nothing to start.")
    val commandLine = GeneralCommandLine(path).withWorkDirectory(workingDir)
    programArguments?.takeIf { it.isNotBlank() }?.let { commandLine.addParameters(ParametersListUtil.parse(it)) }
    return commandLine
  }

  // Run without the debugger: the program alone.
  override fun createProcess(state: CommandLineState): ProcessHandler =
    KillableColoredProcessHandler(programCommandLine())

  override fun createDebugProcess(state: CommandLineState, session: XDebugSession): XDebugProcess {
    val toolchain = CPPToolchains.getInstance().defaultToolchain
      ?: throw ExecutionException("Set up a toolchain (Settings | Build, Execution, Deployment | Toolchains) to debug S++ programs.")
    val lldb = when (toolchain.debuggerKind) {
      CPPDebugger.Kind.BUNDLED_LLDB, CPPDebugger.Kind.CUSTOM_LLDB -> true
      CPPDebugger.Kind.BUNDLED_GDB, CPPDebugger.Kind.CUSTOM_GDB -> false
      else -> throw ExecutionException("S++ programs are debugged with GDB or LLDB; choose one in the default toolchain.")
    }
    val driver = if (lldb) CLionLLDBDriverConfiguration(project, toolchain) else CLionGDBDriverConfiguration(project, toolchain)
    val parameters = TrivialRunParameters(driver, programCommandLine())

    // CLion calls this off the UI thread, but a debug process must be made on
    // it, as CLion's own launchers make theirs.
    return runOnEDT(ThrowableComputable<XDebugProcess, ExecutionException> {
      SppDebugProcess(parameters, session, state.consoleBuilder, workingDir, lldb)
    })
  }
}

// The debug process of an S++ program: CLion's own, with what
// S++ needs when the target is loaded and when it stops.
class SppDebugProcess(
  parameters: RunParameters,
  session: XDebugSession,
  consoleBuilder: TextConsoleBuilder,
  private val projectRoot: String,
  private val lldb: Boolean,
) : CidrLocalDebugProcess(parameters, session, consoleBuilder) {

  // The symbol sppc stops on just before a failed check or an
  // abort ends the process ("sppc_debug_fatal"): a failure ends
  // the program with "_exit", which a debugger would otherwise
  // see only as an exit.
  private val fatalSymbol = "sppc_debug_fatal"

  // Before any breakpoint is set: the compiler records source
  // paths relative to the project, in the directory ".", so the
  // debugger is told where "." is, to match a breakpoint in a
  // file to its lines and to show the file a frame is in. Then
  // the failure breakpoint, which stays pending until libsppc
  // has loaded. Either failing costs only that, so the session
  // still starts.
  override fun doLoadTarget(driver: DebuggerDriver): DebuggerDriver.Inferior {
    val inferior = super.doLoadTarget(driver)
    val root = quoted(projectRoot)
    runCatching {
      driver.executeInterpreterCommand(
        if (lldb) "settings set target.source-map . $root" else "set substitute-path . $root"
      )
    }.onFailure { LOG.warn("Could not map S++ source paths to $projectRoot", it) }
    runCatching { driver.addSymbolicBreakpoint(fatalSymbol) }
      .onFailure { LOG.warn("Could not break on $fatalSymbol", it) }
    loadFormatters(driver)
    return inferior
  }

  // The std's formatters for this debugger, which show a string's
  // text, a vector's elements and a variant's member rather than
  // their raw fields. They ship with the std, beside the layouts
  // they read ("vcs/std/etc/gdb", "etc/lldb"), so the ones loaded
  // are always this project's std's. GDB's "source" takes the rest
  // of the line as the file name, quotes and all, so its path is
  // given bare; LLDB's is quoted. A failed load is reported in the
  // command's output rather than thrown, so the output is checked.
  private fun loadFormatters(driver: DebuggerDriver) {
    val script = Path.of(projectRoot, "vcs", "std", "etc", if (lldb) "lldb" else "gdb", "spp_std.py")
    if (!script.toFile().isFile) {
      LOG.info("No std formatters at $script")
      return
    }
    val command = if (lldb) "command script import ${quoted(script.toString())}" else "source $script"
    runCatching { driver.executeInterpreterCommand(command) }
      .onSuccess { output ->
        if (LOAD_ERRORS.any { output.contains(it, ignoreCase = true) }) {
          LOG.warn("Could not load the std's formatters from $script: ${output.trim()}")
        }
      }
      .onFailure { LOG.warn("Could not load the std's formatters from $script", it) }
  }

  override fun sessionInitialized() {
    super.sessionInitialized()
    session.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() = selectOwnFrame()
    })
  }

  // Stopped where there is no source to show (in sppc, after a
  // failure, or in the C library on a signal): select the first
  // frame in the project's own code instead, not the std's in
  // "vcs/", else the first with any source. A stop at a line (a
  // breakpoint, a step) is left where it is.
  private fun selectOwnFrame() {
    val stack = session.suspendContext?.activeExecutionStack ?: return
    if (stack.topFrame?.sourcePosition != null) return

    val done = AtomicBoolean(false)
    var anySource: XStackFrame? = null
    stack.computeStackFrames(1, object : XExecutionStack.XStackFrameContainer {
      override fun addStackFrames(frames: List<XStackFrame>, last: Boolean) {
        if (done.get()) return
        anySource = anySource ?: frames.firstOrNull { it.sourcePosition != null }
        val chosen = frames.firstOrNull { isOwnCode(it) } ?: if (last) anySource else null
        if (chosen != null || last) done.set(true)
        chosen?.let { frame ->
          ApplicationManager.getApplication().invokeLater { session.setCurrentStackFrame(stack, frame) }
        }
      }

      override fun errorOccurred(errorMessage: String) {}
    })
  }

  private fun isOwnCode(frame: XStackFrame): Boolean {
    val file = frame.sourcePosition?.file?.path?.let { Path.of(it).normalize() } ?: return false
    val root = Path.of(projectRoot).normalize()
    return file.startsWith(root) && !file.startsWith(root.resolve("vcs"))
  }

  // A path as one argument to either debugger's command.
  private fun quoted(path: String): String = "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private companion object {
    val LOG = logger<SppDebugProcess>()

    // What GDB and LLDB print when a script does not load.
    val LOAD_ERRORS = listOf("error", "no such file", "traceback")
  }
}
