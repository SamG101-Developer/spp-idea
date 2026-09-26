package com.intellij.lang

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor

// This class is responsible for ensuring that S++ files end
// with a single trailing newline, as a uniform formatting rule.
class SppTrailingNewlinePostFormatProcessor : PostFormatProcessor {
  // The steps to "process an element" are to just return it.
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

  // The steps to "process text" are to ensure that S++ files
  // end with a single trailing newline. This is done in a
  // write command action, so that it can be undone or redone.
  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (source.language != SppLanguage.INSTANCE) return rangeToReformat
    val doc = PsiDocumentManager.getInstance(source.project).getDocument(source) ?: return rangeToReformat
    val project = source.project

    // Defer until after the formatter has committed its own
    // changes to the document.
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      WriteCommandAction.runWriteCommandAction(project) {
        val text = doc.text
        val cleaned = text.replace(Regex("[ \t]+$", RegexOption.MULTILINE), "").trimEnd() + "\n"
        if (text != cleaned) doc.replaceString(0, doc.textLength, cleaned)
      }
    }

    return rangeToReformat
  }
}
