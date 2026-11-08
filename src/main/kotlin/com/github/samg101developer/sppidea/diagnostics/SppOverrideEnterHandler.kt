package com.github.samg101developer.sppidea.diagnostics

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.lang.SppLanguage
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

// Opens the completion menu of methods to override when Enter
// is pressed straight after a "sup Name ext SuperCls {" header,
// so the superclass's abstract and virtual methods are offered
// as soon as the block is started.
class SppOverrideEnterHandler : EnterHandlerDelegateAdapter() {
  override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
    if (file.language != SppLanguage.INSTANCE) return EnterHandlerDelegate.Result.Continue
    if (!SppOverrideCompletion.caretOnEmptyLine(editor)) return EnterHandlerDelegate.Result.Continue

    // Only for the line straight after the block's "{": Enter
    // anywhere else in the block is just a new line.
    val document = editor.document
    val text = document.charsSequence
    val offset = editor.caretModel.offset
    val block = SppOverrideLookup.enclosingBlock(text, offset) ?: return EnterHandlerDelegate.Result.Continue
    if (document.getLineNumber(block.open) != document.getLineNumber(offset) - 1) {
      return EnterHandlerDelegate.Result.Continue
    }

    // Known to the last analysis: open the menu now. Typed since
    // it: analyse, and open the menu when the answer is in.
    if (!SppOverrideCompletion.popupIfKnown(editor, file)) {
      SppOverrideCompletion.popupWhenAnalysed(editor, file)
    }
    return EnterHandlerDelegate.Result.Continue
  }
}
