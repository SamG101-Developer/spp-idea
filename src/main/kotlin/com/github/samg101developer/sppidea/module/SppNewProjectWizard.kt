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
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.lang.SppIcons
import com.intellij.openapi.module.GeneralModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
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
      val builder = ModuleTypeManager.getInstance().findByID(GeneralModuleType.TYPE_ID).createModuleBuilder()
      setupProjectFromBuilder(project, builder) ?: return

      // The project may still be being created (New Project) or
      // already open (New Module); either way, wait until it is
      // open so "spp init" has somewhere to report to.
      val data = baseData ?: return
      val path = Path.of(data.path, data.name)
      StartupManager.getInstance(project).runAfterOpened {
        val contentRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return@runAfterOpened
        SppProjectScaffolder.scaffold(project, contentRoot)
      }
    }
  }
}
