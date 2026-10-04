package com.github.samg101developer.sppidea.module

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

// Turns a freshly created, empty project directory into an
// S++ project: runs "spp init" in it, then marks the resulting
// `src`/`tst` folders as source/test roots. Shared by the New
// Project wizard in IntelliJ IDEA and the project generator
// used by CLion and the other smaller IDEs.
object SppProjectScaffolder {

  // Run "spp init" in the background, as it can take a moment,
  // then pick up the files it made. The project must already
  // be open, so that the progress and any notifications have
  // somewhere to show.
  fun scaffold(project: Project, contentRoot: VirtualFile) {
    object : Task.Backgroundable(project, "Initializing S++ project (spp init)", false) {
      override fun run(indicator: ProgressIndicator) {
        runSppInit(contentRoot.path)
      }

      override fun onSuccess() {
        VfsUtil.markDirtyAndRefresh(false, true, true, contentRoot)
        val module = ModuleUtilCore.findModuleForFile(contentRoot, project)
          ?: ModuleManager.getInstance(project).modules.singleOrNull()
          ?: return
        WriteAction.run<Throwable> { addSourceRoots(module, contentRoot) }
      }
    }.queue()
  }

  // Perform some validation and then run "spp init" in the
  // content root. If the S++ executable is not configured,
  // or if "spp init" fails, show a notification to the user.
  private fun runSppInit(workingDir: String) {
    val sppPath = resolveSppExecutable()

    // Initial validation: if the S++ executable is not configured,
    // show a warning notification and return early.
    if (sppPath == null) {
      notify(
        "S++ executable is not configured",
        "Set it in Settings | Languages & Frameworks | S++, then run 'spp init' manually in $workingDir.",
        NotificationType.WARNING,
      )
      return
    }

    // Run "spp init" in the content root, capturing its output.
    // If it fails (non-zero exit code or timeout), show an error
    // notification.
    try {
      val commandLine = GeneralCommandLine(sppPath, "init").withWorkDirectory(workingDir)
      val output = CapturingProcessHandler(commandLine).runProcess(30_000)
      if (output.isTimeout || output.exitCode != 0) {
        notify(
          "'spp init' failed",
          (output.stdout + "\n" + output.stderr).trim().ifBlank { "spp init exited with code ${output.exitCode}" },
          NotificationType.ERROR,
        )
      }
    } catch (e: Exception) {
      notify("Failed to run 'spp init'", e.message ?: e.toString(), NotificationType.ERROR)
    }
  }

  // Add the `src` and `tst` folders in the content root as
  // source and test roots, respectively. If they do not exist,
  // they are not added. Todo: FFI?
  private fun addSourceRoots(module: Module, contentRoot: VirtualFile) {
    val model = ModuleRootManager.getInstance(module).modifiableModel
    try {
      val contentEntry = model.contentEntries.firstOrNull { it.file == contentRoot }
        ?: model.addContentEntry(contentRoot)
      contentRoot.findChild("src")?.let { contentEntry.addSourceFolder(it, false) }
      contentRoot.findChild("tst")?.let { contentEntry.addSourceFolder(it, true) }
      model.commit()
    } catch (e: Throwable) {
      model.dispose()
      throw e
    }
  }

  // Show a notification to the user with the given title, content,
  // and type (info, warning, error). The notification group is
  // "S++", which is defined in plugin.xml.
  private fun notify(title: String, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("S++")
      .createNotification(title, content, type)
      .notify(null)
  }
}
