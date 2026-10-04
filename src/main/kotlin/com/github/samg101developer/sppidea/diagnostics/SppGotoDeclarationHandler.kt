package com.github.samg101developer.sppidea.diagnostics

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.SppLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.nio.file.Path
import kotlin.io.path.absolute

// Jump to the declaration of a name, using what the compiler
// worked out when it last analysed the file. Only what is
// already known is used: resolving a name requires compiling
// the project, and this is performed on the editor's thread.
class SppGotoDeclarationHandler : GotoDeclarationHandler {

  // Allow ctrl+click to navigate to the declaration of a
  // name. The compiler's diagnostics service is used to find
  // the declaration, and the editor is asked to open it.
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor,
  ): Array<PsiElement>? {
    // Get basic element, project and file information, and bail
    // if any of that is not available.
    val element = sourceElement ?: return null
    if (element.language != SppLanguage.INSTANCE) return null
    val project = element.project
    val file = element.containingFile?.virtualFile?.toNioPathOrNull()?.absolute() ?: return null

    // Nothing is known about this file yet (it has not been
    // analysed since it was opened). Start that now, in the
    // background, so the answer is there rather than the
    // click having to be repeated until a highlighting pass
    // happens to have filled it in.
    val service = SppCompilerDiagnostics.getInstance(project)
    val symbols = service.cachedSymbolsFor(file)
    if (symbols.isEmpty()) {
      element.containingFile?.virtualFile?.let { service.warmUp(it) }
      return null
    }

    // Lookup the symbol at the caret's offset, and bail if
    // there is none. The symbol's definition is then used
    // to find the file and position of the declaration. If
    // the definition is spp-generated, it has no source to
    // navigate to, so bail in that case too. The definition's
    // file is then looked up in the local file system, and
    // the PSI file and document are found. The offset of the
    // definition in the document is computed, and the PSI
    // element at that offset is returned as the target of the
    // navigation.
    val symbol = SppSymbolLookup.symbolAt(symbols, offset, editor.document) ?: return null

    val target = symbol.definition
    if (target.generated) return null

    // Nothing here may throw: this runs on the editor's thread
    // as part of the navigation itself, and a target the editor
    // cannot open, like a file outside the project, one that has
    // moved, or, a position in a file since edited, causes the
    // "no target" message rather than to fail the action.
    return runCatching {
      val targetFile = LocalFileSystem.getInstance().findFileByNioFile(Path.of(target.file)) ?: return null
      val targetPsi = PsiManager.getInstance(project).findFile(targetFile) ?: return null
      val targetDocument = targetPsi.viewProvider.document ?: return null
      val targetOffset = SppSymbolLookup.offsetOf(target, targetDocument) ?: return null

      // The element at the declaration, rather than the file:
      // navigating to a file opens it at the top.
      val declaration = targetPsi.findElementAt(targetOffset.coerceIn(0, targetPsi.textLength)) ?: return null
      arrayOf(declaration)
    }.getOrNull()
  }
}

private fun com.intellij.openapi.vfs.VirtualFile.toNioPathOrNull(): Path? =
  runCatching { toNioPath() }.getOrNull()
