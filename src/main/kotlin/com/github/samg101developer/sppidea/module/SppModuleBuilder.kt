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

/**
 * Creates a new S++ project/module: adds the content root, then (once the project has finished
 * opening) runs `spp init` in it and marks the resulting `src`/`tst` folders as source/test roots.
 */
class SppModuleBuilder : ModuleBuilder() {

    override fun getModuleType(): ModuleType<*> = SppModuleType.getInstance()

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        doAddContentEntry(modifiableRootModel)
    }

    // New Project flow: the project is still being created, so defer `spp init` until it's open.
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
    override fun postCommit(project: Project, projectDir: VirtualFile) {
        val module = ModuleUtilCore.findModuleForFile(projectDir, project) ?: return
        scaffoldProject(module, projectDir)
    }

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

    private fun runSppInit(workingDir: String) {
        val sppPath = resolveSppExecutable()
        if (sppPath == null) {
            notify(
                "S++ executable is not configured",
                "Set it in Settings | Languages & Frameworks | S++, then run 'spp init' manually in $workingDir.",
                NotificationType.WARNING,
            )
            return
        }
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

    private fun notify(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("S++")
            .createNotification(title, content, type)
            .notify(null)
    }
}
