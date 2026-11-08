package com.github.samg101developer.sppidea.module

import com.intellij.ide.IdeView
import com.intellij.lang.SppIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

// The "New | S++ FFI Package" action. An FFI package is a folder
// in a project's "ffi" folder holding a "stub.spp" that declares
// what the library exports; the compiler finds the library itself
// in the package's target folders ("x86_64-linux-gnu/libname.so"),
// which are added by hand. This makes the folder and an empty stub.
//
// Run on an "ffi" folder, the package goes in it. Anywhere else,
// it goes in the "ffi" folder at the content root, which is made
// if it is missing.
class SppCreateFfiPackageAction : AnAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val view = e.getData(LangDataKeys.IDE_VIEW)
    e.presentation.isEnabledAndVisible = e.project != null && view != null && view.directories.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val view = e.getData(LangDataKeys.IDE_VIEW) ?: return
    val selected = view.getOrChooseDirectory() ?: return
    val parent = ffiFolderFor(project, selected.virtualFile)

    val name = Messages.showInputDialog(
      project,
      "Package name (the namespace it is used by, and the library's name: lib<name>.so):",
      "New S++ FFI Package",
      SppIcons.FILE,
      null,
      NameValidator(parent),
    ) ?: return

    val stub = WriteCommandAction.writeCommandAction(project)
      .withName("Create S++ FFI Package $name")
      .compute<VirtualFile, Throwable> {
        val ffi = parent.existing ?: parent.root.createChildDirectory(this, FFI_FOLDER)
        ffi.createChildDirectory(this, name).createChildData(this, STUB_FILE)
      }

    selectIn(view, project, stub)
  }

  private fun selectIn(view: IdeView, project: Project, stub: VirtualFile) {
    FileEditorManager.getInstance(project).openFile(stub, true)
    PsiManager.getInstance(project).findFile(stub)?.let { view.selectElement(it) }
  }

  // Where the new package goes: the selected folder when it is an
  // "ffi" folder, else the content root's "ffi" (which may not
  // exist yet, so the root is kept to make it in).
  private fun ffiFolderFor(project: Project, selected: VirtualFile): FfiFolder {
    if (selected.name == FFI_FOLDER) return FfiFolder(selected, selected)
    val root = ProjectFileIndex.getInstance(project).getContentRootForFile(selected) ?: selected
    return FfiFolder(root, root.findChild(FFI_FOLDER)?.takeIf { it.isDirectory })
  }

  private class FfiFolder(val root: VirtualFile, val existing: VirtualFile?)

  // A package's name is a namespace in S++ code and part of its
  // library's file name, so it is held to an identifier.
  private class NameValidator(private val parent: FfiFolder) : InputValidatorEx {
    override fun getErrorText(inputString: String): String? = when {
      inputString.isEmpty() -> "Enter a name"
      !IDENTIFIER.matches(inputString) -> "Use letters, digits and underscores, not starting with a digit"
      parent.existing?.findChild(inputString) != null -> "'$FFI_FOLDER/$inputString' already exists"
      else -> null
    }

    override fun checkInput(inputString: String): Boolean = getErrorText(inputString) == null

    override fun canClose(inputString: String): Boolean = checkInput(inputString)
  }

  companion object {
    const val FFI_FOLDER = "ffi"
    const val STUB_FILE = "stub.spp"
    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
  }
}
