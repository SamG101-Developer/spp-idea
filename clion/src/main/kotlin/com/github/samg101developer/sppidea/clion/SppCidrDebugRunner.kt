package com.github.samg101developer.sppidea.clion

import com.github.samg101developer.sppidea.run.SppBuildLauncher
import com.github.samg101developer.sppidea.run.SppConfigurationKind
import com.github.samg101developer.sppidea.run.SppModeTarget
import com.github.samg101developer.sppidea.run.SppRunProfile
import com.github.samg101developer.sppidea.run.SppRunSupport
import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.github.samg101developer.sppidea.settings.sppExecutableMissingMessage
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.CidrRunner
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise

// Debugs an S++ program in CLion: builds it ("spp build", shown
// in the Build tool window as the Build action's is), asks "spp"
// where it put the executable ("spp info executable"), and starts
// that under CLion's native debugger (SppDebugLauncher). Only a
// program's configuration is debugged; "spp test" runs the tests
// itself, so a test configuration has no Debug.
class SppCidrDebugRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "SppCidrDebugRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean =
    executorId == DefaultDebugExecutor.EXECUTOR_ID &&
      profile is SppCidrRunConfiguration && profile.kind == SppConfigurationKind.SOURCE

  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val profile = environment.runProfile as SppRunProfile
    val cidrState = state as? CidrCommandLineState
    val launcher = cidrState?.launcher as? SppDebugLauncher
      ?: return rejectedPromise(ExecutionException("'${profile.name}' cannot be debugged."))
    val sppPath = resolveSppExecutable(profile.executableName)
      ?: return rejectedPromise(ExecutionException(sppExecutableMissingMessage(profile.executableName)))
    val workingDir = SppRunSupport.resolveWorkingDirectory(profile)
      ?: return rejectedPromise(ExecutionException("Select a module for '${profile.name}' before debugging it."))
    val mode = SppModeTarget.modeFor(profile, environment.executionTarget)

    // The build reports to the Build tool window; once it has
    // succeeded, the path is asked for and the session started
    // back on the UI thread.
    val promise = AsyncPromise<RunContentDescriptor?>()
    val app = ApplicationManager.getApplication()
    SppBuildLauncher.build(environment.project, profile, mode, forDebugger = true) { built ->
      if (!built) {
        promise.setError(ExecutionException("'spp build' failed, so there is nothing to debug; see the Build tool window."))
        return@build
      }
      app.executeOnPooledThread {
        try {
          launcher.executable = executablePath(sppPath, workingDir, mode.cliArg)
        } catch (e: ExecutionException) {
          promise.setError(e)
          return@executeOnPooledThread
        }
        app.invokeLater({
          try {
            val session = SessionStarter.startDebugSession(cidrState, environment, false)
            promise.setResult(session.runContentDescriptor)
          } catch (e: ExecutionException) {
            promise.setError(e)
          }
        }, ModalityState.any())
      }
    }
    return promise
  }

  // Where "spp build" put the executable: the one line
  // "spp info executable" prints, which builds nothing.
  private fun executablePath(sppPath: String, workingDir: String, mode: String): String {
    val commandLine = GeneralCommandLine(sppPath, "info", "executable", "--mode", mode)
      .withWorkDirectory(workingDir)
    val output = CapturingProcessHandler(commandLine).runProcess(PRINT_TIMEOUT_MS)
    val path = output.stdout.lines().lastOrNull { it.isNotBlank() }?.trim()
    if (output.isTimeout || output.exitCode != 0 || path == null) {
      throw ExecutionException(
        "'${commandLine.commandLineString}' did not say where the executable is: " +
          output.stderr.trim().ifEmpty { "exit code ${output.exitCode}" }
      )
    }
    return path
  }

  // CLion's own start of a debug session for a CIDR command line
  // state, which also tells the launcher once it has started.
  private object SessionStarter : CidrRunner() {
    override fun getRunnerId(): String = "SppCidrDebugSessionStarter"
  }

  private companion object {
    const val PRINT_TIMEOUT_MS = 60_000
  }
}
