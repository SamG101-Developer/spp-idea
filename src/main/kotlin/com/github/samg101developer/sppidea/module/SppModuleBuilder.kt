package com.github.samg101developer.sppidea.module

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

// Creates a new S++ project/module: adds the content root,
// then (once the project has finished opening) runs "spp init"
// in it and marks the resulting `src`/`tst` folders as source/
// test roots.
class SppModuleBuilder : ModuleBuilder() {

  // The module type is S++, which is used to identify the
  // module in the project view and to create new S++ projects/
  // modules.
  override fun getModuleType(): ModuleType<*> = SppModuleType.getInstance()

  // The content entry is added to the module, which is the
  // root folder of the project/module. This is done before
  // the project is opened, so that the content root is available
  // for the "spp init" command to run in.
  override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
    doAddContentEntry(modifiableRootModel)
  }

  // New Project flow: the project is still being created, so
  // defer "spp init" until it's open.
  override fun commitModule(project: Project, model: ModifiableModuleModel?): Module? {
    val module = super.commitModule(project, model)
    if (module != null) {
      val contentRoot = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()
      if (contentRoot != null) {
        StartupManager.getInstance(project).runAfterOpened { scaffoldProject(module, contentRoot) }
      }
    }
    return module
  }

  // New Module (added into an already-open project) flow.
  // The project is already open, so run "spp init"
  // immediately.
  override fun postCommit(project: Project, projectDir: VirtualFile) {
    val module = ModuleUtilCore.findModuleForFile(projectDir, project) ?: return
    scaffoldProject(module, projectDir)
  }

  // Scaffold the project by running "spp init" in the content
  // root, then marking the resulting `src`/`tst` folders as
  // source/test roots.
  private fun scaffoldProject(module: Module, contentRoot: VirtualFile) {
    val project = module.project
    object : Task.Backgroundable(project, "Initializing S++ project (spp init)", false) {
      override fun run(indicator: ProgressIndicator) {
        runSppInit(contentRoot.path)
      }

      override fun onSuccess() {
        VfsUtil.markDirtyAndRefresh(false, true, true, contentRoot)
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
