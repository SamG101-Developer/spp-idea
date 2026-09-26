package com.github.samg101developer.sppidea.run

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.atomic.AtomicBoolean

// Runs the "spp build" action, and reports it to the "build"
// tool window, not a run console.
object SppBuildLauncher {

  fun build(project: Project, configuration: SppRunConfiguration) {
    if (!configuration.kind.buildable) return

    // Ensure the s++ executable has been provided in the settings.
    val sppPath = resolveSppExecutable() ?: return notifyFailure(
      project,
      "The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++.",
    )

    // Ensure the working directory is set, which is the module's
    // content root. Todo: Allow manual selection of CWD in the
    // fun config.
    val workingDir = configuration.resolveWorkingDirectory() ?: return notifyFailure(
      project,
      "Select a module for '${configuration.name}' before building it.",
    )

    // Create the command line and start the process. If it fails
    // to start, report it as a notification.
    val commandLine = sppCommandLine(sppPath, SppCommand.BUILD, workingDir, configuration.programArguments)
    val handler = try {
      startSppProcess(commandLine)
    } catch (e: ExecutionException) {
      return notifyFailure(project, e.message ?: "Could not start '${commandLine.commandLineString}'.")
    }

    // A stopped build is reported as cancelled rather than failed,
    // which the exit code alone cannot distinguish: killing the
    // compiler also gives it a non-zero code.
    val cancelled = AtomicBoolean(false)
    val buildDescriptor = DefaultBuildDescriptor(Any(), configuration.name, workingDir, System.currentTimeMillis())
      .withAction(StopBuildAction(handler, cancelled))
      .withRestartAction(RestartBuildAction(project, configuration, handler))
    buildDescriptor.isActivateToolWindowWhenAdded = true

    // Create a progress object for the build, and listen to the
    // process events to report them to the build console.
    val progress = BuildViewManager.createBuildProgress(project)
      .start(BuildProgressDescriptorImpl("Running 'spp build'…", buildDescriptor))

    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        // The handler has already stripped the escapes and encoded
        // the colour into the output type, so the build console can
        // be given the text as-is.
        progress.output(event.text, ProcessOutputType.fromKey(outputType) ?: ProcessOutputType.STDOUT)
      }

      override fun processTerminated(event: ProcessEvent) {
        when {
          cancelled.get() -> progress.cancel()
          event.exitCode == 0 -> progress.finish(
            System.currentTimeMillis(), "build finished", SuccessResultImpl(),
          )

          else -> progress.finish(
            System.currentTimeMillis(),
            "build failed",
            FailureResultImpl("'spp build' exited with code ${event.exitCode}"),
          )
        }
      }
    })
    handler.startNotify()
  }

  // Report a failure to start the build as a notification,
  // which is more visible than the build tool window, which
  // is not opened until the build starts.
  private fun notifyFailure(project: Project, message: String) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("S++")
      .createNotification("Cannot build", message, NotificationType.ERROR)
      .notify(project)
  }

  // The "Stop" action in the build tool window, which terminates
  // the build process and marks it as cancelled.
  private class StopBuildAction(
    private val handler: ProcessHandler,
    private val cancelled: AtomicBoolean,
  ) : AnAction("Stop", "Terminate 'spp build'", AllIcons.Actions.Suspend), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !handler.isProcessTerminated
    }

    override fun actionPerformed(e: AnActionEvent) {
      cancelled.set(true)
      handler.destroyProcess()
    }
  }

  // The "Rerun" action in the build tool window, which starts a new
  // build with the same configuration. It is only enabled when the
  // previous build has finished.
  private class RestartBuildAction(
    private val project: Project,
    private val configuration: SppRunConfiguration,
    private val handler: ProcessHandler,
  ) : AnAction("Rerun Build", "Run 'spp build' again", AllIcons.Actions.Restart), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = handler.isProcessTerminated
    }

    override fun actionPerformed(e: AnActionEvent) {
      build(project, configuration)
    }
  }
}
