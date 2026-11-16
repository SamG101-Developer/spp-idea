package com.github.samg101developer.sppidea.run

import com.github.samg101developer.sppidea.settings.sppExecutableMissingMessage
import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.isRegularFile

// Toolbar action that runs "spp vcs", which fetches and updates
// the project's [vcs] dependencies into its "vcs" folder.
class SppVcsAction : AnAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  // Shown for any S++ project; disabled while an update for the
  // same project is already running.
  override fun update(e: AnActionEvent) {
    val project = e.project
    val directory = project?.let { SppVcsLauncher.projectDirectory(it) }
    e.presentation.isVisible = directory != null
    e.presentation.isEnabled = directory != null && !SppVcsLauncher.isRunning(directory)
    e.presentation.icon = AllIcons.Actions.Refresh
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    SppVcsLauncher.update(project)
  }
}

// Runs "spp vcs" and reports it to the "build" tool window, the
// same way the build action reports "spp build".
object SppVcsLauncher {

  private val running = ConcurrentHashMap.newKeySet<String>()

  fun isRunning(directory: String) = directory in running

  // The S++ project to update: the selected S++ configuration's
  // module, or else the IDE project's root when it is an S++
  // project itself.
  fun projectDirectory(project: Project): String? {
    val selected = RunManager.getInstanceIfCreated(project)?.selectedConfiguration?.configuration as? SppRunProfile
    selected?.let { SppRunSupport.resolveWorkingDirectory(it) }?.let { return it }
    val base = project.basePath?.let { Path.of(it) } ?: return null
    return base.takeIf { it.resolve(SppRunSupport.PROJECT_FILE).isRegularFile() }?.toString()
  }

  fun update(project: Project) {
    val workingDir = projectDirectory(project) ?: return notifyFailure(
      project, "Open an S++ project, or select an S++ run configuration, to update its dependencies.",
    )
    val sppPath = SppRunSupport.executableFor(project) ?: return notifyFailure(
      project, sppExecutableMissingMessage(
        (RunManager.getInstance(project).selectedConfiguration?.configuration as? SppRunProfile)?.executableName
      ),
    )
    if (!running.add(workingDir)) return

    // Starting a process is not allowed on the UI thread.
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        start(project, sppPath, workingDir)
      } catch (e: Throwable) {
        running.remove(workingDir)
        throw e
      }
    }
  }

  private fun start(project: Project, sppPath: String, workingDir: String) {
    val commandLine = sppCommandLine(sppPath, SppCommand.VCS, workingDir, null, null)
    val handler = try {
      startSppProcess(commandLine)
    } catch (e: ExecutionException) {
      running.remove(workingDir)
      return notifyFailure(project, e.message ?: "Could not start '${commandLine.commandLineString}'.")
    }

    val cancelled = AtomicBoolean(false)
    val descriptor = DefaultBuildDescriptor(
      Any(), "${Path.of(workingDir).fileName} | vcs", workingDir, System.currentTimeMillis(),
    ).withAction(SppBuildLauncher.StopBuildAction(handler, cancelled, "Terminate 'spp vcs'"))
    descriptor.isActivateToolWindowWhenAdded = true

    val progress = BuildViewManager.createBuildProgress(project)
      .start(object : BuildProgressDescriptor {
        override fun getTitle(): String = "Running 'spp vcs'…"
        override fun getBuildDescriptor(): BuildDescriptor = descriptor
      })

    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        progress.output(event.text, ProcessOutputType.fromKey(outputType) ?: ProcessOutputType.STDOUT)
      }

      override fun processTerminated(event: ProcessEvent) {
        running.remove(workingDir)
        when {
          cancelled.get() -> progress.cancel()
          event.exitCode == 0 -> progress.finish(
            System.currentTimeMillis(), "dependencies updated", SuccessResultImpl(),
          )

          else -> progress.finish(
            System.currentTimeMillis(), "dependency update failed",
            FailureResultImpl("'spp vcs' exited with code ${event.exitCode}"),
          )
        }

        // Let the IDE see the fetched or updated dependency files.
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(workingDir))?.let {
          VfsUtil.markDirtyAndRefresh(true, true, true, it)
        }
      }
    })
    handler.startNotify()
  }

  private fun notifyFailure(project: Project, message: String) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("S++")
      .createNotification("Cannot update dependencies", message, NotificationType.ERROR)
      .notify(project)
  }
}
