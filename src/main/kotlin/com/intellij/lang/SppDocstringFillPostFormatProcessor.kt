package com.intellij.lang

import com.intellij.lang.psi.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil

private val NUMBERED_ITEM_RE = Regex("""^\d+\. """)

// This class is responsible for auto-formatting docstrings,
// including reflowing them to the right margin.
class SppDocstringFillPostFormatProcessor : PostFormatProcessor {

  // The steps to "process an element" are to just return it.
  // In the future, the settings might be used to apply extra
  // logic.
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

  // The steps to "process text" are to reflow docstrings in
  // the source file, if it is an S++ file. The reflowing is
  // done in a write command action, so that it can be undone
  // or redone.
  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (source.language != SppLanguage.INSTANCE) return rangeToReformat
    val project = source.project
    val doc = PsiDocumentManager.getInstance(project).getDocument(source) ?: return rangeToReformat
    val rightMargin = settings.getRightMargin(SppLanguage.INSTANCE)

    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      WriteCommandAction.runWriteCommandAction(project) {

        // Snapshot the document beforehand to create a "restore"
        // point, that can be used from undo/redo instructions.
        val psiManager = PsiDocumentManager.getInstance(project)
        psiManager.commitDocument(doc)
        val file = psiManager.getPsiFile(doc) ?: return@runWriteCommandAction

        // Collect potentially documented implementation nodes,
        // and collect their docstrings if they exist.
        val replacements = mutableListOf<Pair<TextRange, String>>()
        for (impl in collectImpls(file)) {
          val comments = collectDocstringComments(impl)
          if (comments.isEmpty()) continue
          reflowCommentGroup(comments, doc, rightMargin)?.let { replacements += it }
        }

        // Apply in reverse order so earlier offsets aren't
        // shifted by later replacements.
        for ((range, newText) in replacements.sortedByDescending { it.first.startOffset }) {
          doc.replaceString(range.startOffset, range.endOffset, newText)
        }
      }
    }

    return rangeToReformat
  }

  // Get the implementation nodes that can be documented with
  // recognized docstrings.
  private fun collectImpls(file: PsiFile): List<PsiElement> = buildList {
    addAll(PsiTreeUtil.findChildrenOfType(file, SppFunctionImplementation::class.java))
    addAll(PsiTreeUtil.findChildrenOfType(file, SppClassImplementation::class.java))
    addAll(PsiTreeUtil.findChildrenOfType(file, SppSupImplementation::class.java))
  }

  // Collect the docstring comments that are immediately after
  // the implementation node, stopping at the first non-comment
  // or non-whitespace line. Todo: Duplicated SppDocstringAnnotator.
  private fun collectDocstringComments(impl: PsiElement): List<PsiComment> {
    val result = mutableListOf<PsiComment>()
    var child = impl.firstChild?.nextSibling
    while (child != null) {
      when (child) {
        is PsiComment -> result += child
        is PsiWhiteSpace if !child.text.contains("\n\n") -> {}
        else -> break
      }
      child = child.nextSibling
    }
    return result
  }

  // The master reflowing logic function. The strategy is to
  // re-combine all docstring lines for a group into one, and
  // then re-split it on the margin, allowing for in-the-middle
  // changes to be respected.
  private fun reflowCommentGroup(
    comments: List<PsiComment>,
    doc: Document,
    rightMargin: Int,
  ): Pair<TextRange, String>? {
    // Split the multiple docstring paragraphs into their lines,
    // defined by the offset within the document.
    val lines = comments.map { c ->
      val ln = doc.getLineNumber(c.textRange.startOffset)
      doc.getText(TextRange(doc.getLineStartOffset(ln), doc.getLineEndOffset(ln)))
    }

    // Get the indentation, to confirm that there is space between
    // the indent and the right margin (hard wrap), allowing
    // the maximum length to be calculated.
    val indent = lines.first().takeWhile { it == ' ' || it == '\t' }
    val available = rightMargin - indent.length - 2  // 2 = "# "
    if (available <= 0) return null

    // Re-paragraph the docstring by splitting the group into its
    // paragraphs and then handling the paragraphs internally.
    val reflowed = splitIntoParagraphs(lines, indent).flatMap { para ->
      val firstContent = extractContent(para.first(), indent)
      when {
        isBlank(para.first(), indent) -> para
        firstContent.trimStart().startsWith("```") -> para // verbatim code block
        else -> reflowParagraph(para, indent, available)
      }
    }

    if (reflowed == lines) return null

    // Rejoin the reflowed docstring, and return it all.
    val firstLn = doc.getLineNumber(comments.first().textRange.startOffset)
    val lastLn = doc.getLineNumber(comments.last().textRange.startOffset)
    val range = TextRange(doc.getLineStartOffset(firstLn), doc.getLineEndOffset(lastLn))
    return range to reflowed.joinToString("\n")
  }

  // Split lines into paragraphs, by using a comment line
  // containing only "#" as a separator of paragraphs in the
  // docstring.
  private fun splitIntoParagraphs(lines: List<String>, indent: String): List<List<String>> {
    val result = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    var inCodeBlock = false

    for (line in lines) {
      val content = extractContent(line, indent)

      // Code-fence toggle: ``` opens or closes a verbatim block.
      if (content.trimStart().startsWith("```")) {
        if (!inCodeBlock) {
          // Opening fence: flush any current text paragraph, begin
          // verbatim.
          if (current.isNotEmpty()) {
            result += current; current = mutableListOf()
          }
          inCodeBlock = true
        } else {
          inCodeBlock = false
        }
        current += line
        // Closing fence: flush the verbatim paragraph immediately.
        if (!inCodeBlock) {
          result += current; current = mutableListOf()
        }
        continue
      }

      // Inside a code block: accumulate verbatim, no paragraph
      // logic.
      if (inCodeBlock) {
        current += line; continue
      }

      when {
        content.isEmpty() -> {
          if (current.isNotEmpty()) {
            result += current; current = mutableListOf()
          }
          result += mutableListOf(line)
        }

        // Todo: what was the thought behind @ being used as a
        // paragraph separator? IIRC it is to do with tags.
        content.startsWith("@") -> {
          if (current.isNotEmpty()) {
            result += current; current = mutableListOf()
          }
          current = mutableListOf(line)
        }

        // Each list item (bullet or numbered) is its own standalone
        // paragraph so the reflow algorithm never joins multiple
        // items into one line.
        content.startsWith("- ")
            || content.startsWith("* ")
            || NUMBERED_ITEM_RE.containsMatchIn(content) -> {
          if (current.isNotEmpty()) {
            result += current; current = mutableListOf()
          }
          result += mutableListOf(line)
        }

        else -> current += line
      }
    }

    if (current.isNotEmpty()) result += current
    return result
  }

  // Join the paragraph's text content, then re-wrap at the margin
  // (specified by "available").
  private fun reflowParagraph(lines: List<String>, indent: String, available: Int): List<String> {
    val content = lines.joinToString(" ") { extractContent(it, indent) }.trim()
    if (content.isEmpty()) return lines

    val result = mutableListOf<String>()
    var remaining = content
    while (remaining.isNotEmpty()) {
      // When there is not enough docstring left to ever wrap,
      // append it on the new line and break from the loop.
      if (remaining.length <= available) {
        result += "$indent# $remaining"
        break
      }

      // No space within limit => force-break at the column, but
      // never inside a "[name]" reference, which is kept whole on
      // the line it starts on.
      val breakAt = remaining.lastIndexOf(' ', available)
      if (breakAt <= 0) {
        val cut = SppDocstringRefs.findIn(remaining)
          .firstOrNull { available > it.range.first && available <= it.range.last }
          ?.let { it.range.last + 1 }
          ?: available
        result += "$indent# ${remaining.substring(0, cut)}"
        remaining = remaining.substring(cut).trimStart()

      // Break at the last space within the available limit.
      } else {
        result += "$indent# ${remaining.substring(0, breakAt)}"
        remaining = remaining.substring(breakAt + 1)
      }
    }

    // Return the concatenated content.
    return result
  }

  // Text content of a comment line: strip leading whitespace +
  // `#` + optional single space.
  private fun extractContent(line: String, indent: String): String {
    val afterIndent = line.removePrefix(indent)
    val afterHash = afterIndent.removePrefix("#")
    return if (afterHash.startsWith(" ")) afterHash.substring(1).trimEnd() else afterHash.trimEnd()
  }

  // Check if a line is blank, ignoring indentation and the
  // leading `#`.
  private fun isBlank(line: String, indent: String): Boolean =
    line.removePrefix(indent).removePrefix("#").isBlank()
}
