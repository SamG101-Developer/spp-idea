package com.github.samg101developer.sppidea.diagnostics

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.nio.file.Path
import kotlin.io.path.absolute

/**
 * Jumps to where a name was declared, using what the compiler worked out when it last analysed the file.
 *
 * Only what is already known is used: resolving a name means compiling the project, which is seconds, and this is
 * asked on the editor's thread. The annotator is what fills that in, so a file that has been looked at long enough to
 * be highlighted can also be navigated.
 */
class SppGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val project = element.project
        val file = element.containingFile?.virtualFile?.toNioPathOrNull()?.absolute() ?: return null

        // Nothing known about this file yet - it has not been analysed since it was opened. Start that now, in the
        // background, so the answer is there rather than the click having to be repeated until a highlighting pass
        // happens to have filled it in.
        val service = SppCompilerDiagnostics.getInstance(project)
        val symbols = service.cachedSymbolsFor(file)
        if (symbols.isEmpty()) {
            element.containingFile?.virtualFile?.let { service.warmUp(it) }
            return null
        }

        val symbol = SppSymbolLookup.symbolAt(symbols, offset, editor.document) ?: return null

        val target = symbol.definition
        if (target.generated) return null

        // Nothing here may throw: this runs on the editor's thread as part of the navigation itself, and a target the
        // editor cannot open - a file outside the project, one that has moved, a position in a file since edited - is
        // a reason to answer "no target" rather than to fail the action.
        return runCatching {
            val targetFile = LocalFileSystem.getInstance().findFileByNioFile(Path.of(target.file)) ?: return null
            val targetPsi = PsiManager.getInstance(project).findFile(targetFile) ?: return null
            val targetDocument = targetPsi.viewProvider.document ?: return null
            val targetOffset = SppSymbolLookup.offsetOf(target, targetDocument) ?: return null

            // The element at the declaration, rather than the file: navigating to a file opens it at the top.
            val declaration = targetPsi.findElementAt(targetOffset.coerceIn(0, targetPsi.textLength)) ?: return null
            arrayOf<PsiElement>(declaration)
        }.getOrNull()
    }
}

private fun com.intellij.openapi.vfs.VirtualFile.toNioPathOrNull(): Path? =
    runCatching { toNioPath() }.getOrNull()
