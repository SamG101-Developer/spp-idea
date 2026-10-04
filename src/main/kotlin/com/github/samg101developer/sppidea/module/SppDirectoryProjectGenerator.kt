package com.github.samg101developer.sppidea.module

import com.intellij.lang.SppIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import javax.swing.Icon

// The "S++" entry in the New Project dialog of CLion, PyCharm
// and the other IDEs without IntelliJ IDEA's module-based
// wizard. IntelliJ IDEA does not list these, so it does not
// show up twice there; see SppNewProjectWizard for that one.
// There are no settings beyond the location, hence "Any".
class SppDirectoryProjectGenerator : DirectoryProjectGeneratorBase<Any>() {
  override fun getName(): String = "S++"
  override fun getDescription(): String = "An S++ project, scaffolded with 'spp init'"
  override fun getLogo(): Icon = SppIcons.FILE

  // Called once the project exists and is open, in the chosen
  // (empty) directory.
  override fun generateProject(project: Project, baseDir: VirtualFile, settings: Any, module: Module) {
    SppProjectScaffolder.scaffold(project, baseDir)
  }
}
