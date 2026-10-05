package com.github.samg101developer.sppidea.module

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.lang.SppIcons
import com.intellij.openapi.module.GeneralModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path
import javax.swing.Icon

// The "S++" entry in File | New | Project / Module in IntelliJ
// IDEA. The project gets the IDE's generic module type, as S++
// needs nothing from a module type of its own; "spp init" is
// then run in the new directory once the project is open.
class SppNewProjectWizard : GeneratorNewProjectWizard {
  override val id: String = "S++"
  override val name: String = "S++"
  override val icon: Icon = SppIcons.FILE
  override val description: String = "An S++ project, scaffolded with 'spp init'"

  // Name and location, then the "create Git repository" box,
  // then the step that creates the project itself.
  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::NewProjectWizardBaseStep)
      .nextStep(::GitNewProjectWizardStep)
      .nextStep(::Step)

  private class Step(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
    override fun setupProject(project: Project) {
      val data = baseData ?: return
      val path = Path.of(data.path, data.name)

      // A plain module rooted at the new directory, committed
      // directly rather than through the wizard's own (internal)
      // helpers.
      val builder = ModuleTypeManager.getInstance().findByID(GeneralModuleType.TYPE_ID).createModuleBuilder()
      builder.name = data.name
      builder.contentEntryPath = path.toString()
      builder.moduleFilePath = path.resolve("${data.name}.iml").toString()
      builder.commit(project) ?: return

      // New Module: the project is already open, so scaffold now.
      // New Project: it is not open yet, so leave the path for
      // ScaffoldActivity to pick up once it is, so "spp init" has
      // somewhere to report to.
      if (StartupManager.getInstance(project).postStartupActivityPassed()) scaffold(project, path)
      else project.putUserData(PENDING_SCAFFOLD, path)
    }
  }

  // Runs the scaffold left behind by the wizard, once the new
  // project has opened.
  class ScaffoldActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      val path = project.getUserData(PENDING_SCAFFOLD) ?: return
      project.putUserData(PENDING_SCAFFOLD, null)
      scaffold(project, path)
    }
  }

  private companion object {
    val PENDING_SCAFFOLD = Key.create<Path>("spp.pendingScaffold")

    fun scaffold(project: Project, path: Path) {
      val contentRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return
      SppProjectScaffolder.scaffold(project, contentRoot)
    }
  }
}
