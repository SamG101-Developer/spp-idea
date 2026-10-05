package com.github.samg101developer.sppidea.module

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

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
      private var module: Module? = null

      // Refreshing the VFS and finding the module are too slow
      // for the EDT, so they are done here; only the root model
      // change is left for onSuccess.
      override fun run(indicator: ProgressIndicator) {
        runSppInit(project, contentRoot.path)
        VfsUtil.markDirtyAndRefresh(false, true, true, contentRoot)
        module = ReadAction.compute<Module?, Throwable> {
          ModuleUtilCore.findModuleForFile(contentRoot, project)
            ?: ModuleManager.getInstance(project).modules.singleOrNull()
        }
      }

      override fun onSuccess() {
        val module = module ?: return
        WriteAction.run<Throwable> { addSourceRoots(module, contentRoot) }
      }
    }.queue()
  }

  // Perform some validation and then run "spp init" for the
  // content root. If the S++ executable is not configured,
  // or if "spp init" fails, show a notification to the user.
  private fun runSppInit(project: Project, workingDir: String) {
    val sppPath = resolveSppExecutable()

    // Initial validation: if the S++ executable is not configured,
    // show a warning notification and return early.
    if (sppPath == null) {
      notify(
        project,
        "S++ executable is not configured",
        "Set it in Settings | Languages & Frameworks | S++, then run 'spp init' manually in $workingDir.",
        NotificationType.WARNING,
      )
      return
    }

    // "spp init" refuses a directory that is not empty, and the
    // IDE has already written ".idea" (and maybe an ".iml") into
    // the content root. So run it in an empty scratch directory
    // with the same name (it names the project after the folder),
    // then move what it made into the content root. The scratch
    // directory is inside the content root, so the moves are
    // renames on the same filesystem.
    val root = Path.of(workingDir)
    val scratch = Files.createTempDirectory(root, ".spp-init")
    try {
      val initDir = Files.createDirectory(scratch.resolve(root.fileName))
      val commandLine = GeneralCommandLine(sppPath, "init").withWorkDirectory(initDir.toFile())
      val output = CapturingProcessHandler(commandLine).runProcess(30_000)

      // "spp init" reports some errors with a zero exit code, so
      // an "Error:" on stderr counts as a failure too.
      if (output.isTimeout || output.exitCode != 0 || output.stderr.contains("Error:")) {
        notify(
          project,
          "'spp init' failed",
          (output.stdout + "\n" + output.stderr).trim().ifBlank { "spp init exited with code ${output.exitCode}" },
          NotificationType.ERROR,
        )
        return
      }

      // Move the generated files up, leaving anything already in
      // the content root alone.
      Files.list(initDir).use { children ->
        children.forEach { child ->
          val target = root.resolve(child.fileName)
          if (Files.notExists(target)) Files.move(child, target)
        }
      }
    } catch (e: Exception) {
      notify(project, "Failed to run 'spp init'", e.message ?: e.toString(), NotificationType.ERROR)
    } finally {
      runCatching { NioFiles.deleteRecursively(scratch) }
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
  // "S++", which is defined in plugin.xml. It is attached to
  // the project so it is not lost while its frame is opening.
  private fun notify(project: Project, title: String, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("S++")
      .createNotification(title, content, type)
      .notify(project)
  }
}
