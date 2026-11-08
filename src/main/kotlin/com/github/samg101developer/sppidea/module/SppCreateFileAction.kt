package com.github.samg101developer.sppidea.module

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.lang.SppIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

// The "New | S++ File" action shown when right-clicking a directory in the
// project view. Creates a ".spp" file from the internal "S++ File" template.
class SppCreateFileAction : CreateFileFromTemplateAction(), DumbAware {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder
      .setTitle("New S++ File")
      .addKind("S++ file", SppIcons.FILE, TEMPLATE_NAME)
  }

  override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
    "Create S++ File $newName"

  companion object {
    const val TEMPLATE_NAME = "S++ File"
  }
}
