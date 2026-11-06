package com.github.samg101developer.sppidea.diagnostics

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import kotlin.io.path.absolute

// The completion entries for overriding a superclass method in
// a "sup ext" block, and opening the menu of them by itself.
object SppOverrideCompletion {

  // How many runs to wait through for a block to show up in the
  // answers: one already under way may have started before the
  // block was typed, so the one asked for is the next after it.
  private const val RUNS_TO_WAIT = 2

  // One method to override. It is found by its name or by its
  // header, and written as the whole method: the header, then an
  // empty body with the caret in it. Abstract methods (the ones
  // that must be written) come first, in bold.
  fun lookupFor(method: SppOverridable): LookupElement {
    val abstract = method.modifier == "abstract"
    val element = LookupElementBuilder.create(method, method.prototype)
      .withLookupStrings(setOf(method.prototype, method.name))
      .withPresentableText(method.name)
      .withTailText(method.prototype.substringAfter(method.name), true)
      .withTypeText(method.modifier, true)
      .withIcon(if (abstract) AllIcons.Gutter.ImplementingMethod else AllIcons.Gutter.OverridingMethod)
      .withBoldness(abstract)
      .withCaseSensitivity(true)
      .withInsertHandler { context, _ ->
        // Indent the body one step past the line the header is on.
        val document = context.document
        val line = document.getLineNumber(context.startOffset)
        val lineStart = document.getLineStartOffset(line)
        val indent = document.charsSequence.subSequence(lineStart, context.startOffset).takeWhile { it == ' ' || it == '\t' }
        val options = CodeStyle.getIndentOptions(context.file)
        val step = if (options.USE_TAB_CHARACTER) "\t" else " ".repeat(options.INDENT_SIZE)

        // The tail moves with what is inserted at it, so it is read
        // before the insert.
        val tail = context.tailOffset
        val body = " {\n$indent$step"
        document.insertString(tail, "$body\n$indent}")
        context.editor.caretModel.moveToOffset(tail + body.length)
      }
    return PrioritizedLookupElement.withPriority(element, if (abstract) 1.0 else 0.0)
  }

  // Analyse [file] again, and once the answer holds the "sup ext"
  // block the caret is in, open the completion menu there - if
  // the caret is still between that block's members.
  fun popupWhenAnalysed(editor: Editor, file: PsiFile) {
    val project = file.project
    val virtualFile = file.virtualFile ?: return
    val service = SppCompilerDiagnostics.getInstance(project)
    waitForBlock(editor, file, service, RUNS_TO_WAIT)
    service.reanalyse(virtualFile)
  }

  // Open the menu now if the last analysis knows the block at
  // the caret and it has methods left to override; false when
  // it does not know the block.
  fun popupIfKnown(editor: Editor, file: PsiFile): Boolean {
    val pending = pendingAtCaret(editor, file) ?: return false
    if (pending.isEmpty()) return true
    AutoPopupController.getInstance(file.project).scheduleAutoPopup(editor)
    return true
  }

  // After the next run, open the menu if the block is known by
  // then, else keep waiting, through up to [runsLeft] runs.
  private fun waitForBlock(editor: Editor, file: PsiFile, service: SppCompilerDiagnostics, runsLeft: Int) {
    service.whenNextRunLands {
      ApplicationManager.getApplication().invokeLater {
        if (editor.isDisposed || file.project.isDisposed) return@invokeLater
        val pending = pendingAtCaret(editor, file)
        when {
          pending == null -> if (runsLeft > 1) waitForBlock(editor, file, service, runsLeft - 1)
          pending.isNotEmpty() && caretOnEmptyLine(editor) ->
            AutoPopupController.getInstance(file.project).scheduleAutoPopup(editor)
        }
      }
    }
  }

  // What the block at the caret could still override: null when
  // the caret is not between a "sup ext" block's members, or the
  // last analysis does not know the block yet.
  fun pendingAtCaret(editor: Editor, file: PsiFile): List<SppOverridable>? {
    val path = file.virtualFile?.let { runCatching { it.toNioPath().absolute() }.getOrNull() } ?: return null
    val document = editor.document
    val text = document.charsSequence
    val block = SppOverrideLookup.enclosingBlock(text, editor.caretModel.offset) ?: return null
    val lists = SppCompilerDiagnostics.getInstance(file.project).cachedOverridablesFor(path)
    return SppOverrideLookup.pending(lists, block, text, document)
  }

  // Whether nothing but indentation is before the caret on its
  // line, i.e. a member has not been started there yet.
  fun caretOnEmptyLine(editor: Editor): Boolean {
    val document = editor.document
    val offset = editor.caretModel.offset
    val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
    return document.charsSequence.subSequence(lineStart, offset).isBlank()
  }
}
