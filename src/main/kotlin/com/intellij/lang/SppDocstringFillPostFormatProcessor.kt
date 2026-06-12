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

class SppDocstringFillPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

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

                // Apply in reverse order so earlier offsets aren't shifted
                // by later replacements.
                for ((range, newText) in replacements.sortedByDescending { it.first.startOffset }) {
                    doc.replaceString(range.startOffset, range.endOffset, newText)
                }
            }
        }

        return rangeToReformat
    }

    private fun collectImpls(file: PsiFile): List<PsiElement> = buildList {
        // Get the implementation nodes that can be documented
        // with recognized docstrings.
        addAll(PsiTreeUtil.findChildrenOfType(file, SppFunctionImplementation::class.java))
        addAll(PsiTreeUtil.findChildrenOfType(file, SppClassImplementation::class.java))
        addAll(PsiTreeUtil.findChildrenOfType(file, SppSupImplementation::class.java))
    }

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

    private fun reflowCommentGroup(
        comments: List<PsiComment>,
        doc: Document,
        rightMargin: Int,
    ): Pair<TextRange, String>? {
        // Split the multiple docstring paragraphs into their
        // lines, defined by the offset within the document.
        val lines = comments.map { c ->
            val ln = doc.getLineNumber(c.textRange.startOffset)
            doc.getText(TextRange(doc.getLineStartOffset(ln), doc.getLineEndOffset(ln)))
        }

        // Get the indentation, to confirm that there is space
        // between the indent and the right margin (hard wrap),
        // allowing the maximum length to be calculated.
        val indent = lines.first().takeWhile { it == ' ' || it == '\t' }
        val available = rightMargin - indent.length - 2  // 2 = "# "
        if (available <= 0) return null

        // Re-paragraph the docstring by splitting the group into its
        // paragraphs and then handling the paragraphs internally.
        val reflowed = splitIntoParagraphs(lines, indent).flatMap { para ->
            val firstContent = extractContent(para.first(), indent)
            when {
                isBlank(para.first(), indent) -> para
                firstContent.trimStart().startsWith("```") -> para   // verbatim code block
                firstContent.trimStart().startsWith("- ") || firstContent.trimStart().startsWith("* ") -> para  // verbatim bullet
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

    /**
     * Splits lines into paragraphs:
     *  - A blank comment line is its own paragraph (preserved verbatim, acts as separator).
     *  - A line whose content starts with `@` begins a new paragraph.
     *  - All other lines are appended to the current paragraph.
     */
    private fun splitIntoParagraphs(lines: List<String>, indent: String): List<List<String>> {
        val result = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var inCodeBlock = false

        for (line in lines) {
            val content = extractContent(line, indent)

            // Code-fence toggle: ``` opens or closes a verbatim block.
            if (content.trimStart().startsWith("```")) {
                if (!inCodeBlock) {
                    // Opening fence: flush any current text paragraph, begin verbatim.
                    if (current.isNotEmpty()) { result += current; current = mutableListOf() }
                    inCodeBlock = true
                } else {
                    inCodeBlock = false
                }
                current += line
                // Closing fence: flush the verbatim paragraph immediately.
                if (!inCodeBlock) { result += current; current = mutableListOf() }
                continue
            }

            // Inside a code block: accumulate verbatim, no paragraph logic.
            if (inCodeBlock) { current += line; continue }

            when {
                content.isEmpty() -> {
                    if (current.isNotEmpty()) { result += current; current = mutableListOf() }
                    result += mutableListOf(line)
                }
                content.startsWith("@") -> {
                    if (current.isNotEmpty()) { result += current; current = mutableListOf() }
                    current = mutableListOf(line)
                }
                content.startsWith("- ") || content.startsWith("* ") -> {
                    // Each bullet item is its own standalone paragraph so the reflow
                    // algorithm never joins multiple items into one line.
                    if (current.isNotEmpty()) { result += current; current = mutableListOf() }
                    result += mutableListOf(line)
                }
                else -> current += line
            }
        }

        if (current.isNotEmpty()) result += current
        return result
    }

    /** Join the paragraph's text content, then re-wrap at [available] columns. */
    private fun reflowParagraph(lines: List<String>, indent: String, available: Int): List<String> {
        // Strip each line down to prepare it for concatenation.
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
            val breakAt = remaining.lastIndexOf(' ', available)
            if (breakAt <= 0) {
                // No space within limit => force-break at the column.
                result += "$indent# ${remaining.substring(0, available)}"
                remaining = remaining.substring(available).trimStart()
            } else {
                // Break at the last space within the available limit.
                result += "$indent# ${remaining.substring(0, breakAt)}"
                remaining = remaining.substring(breakAt + 1)
            }
        }

        // Return the concatenated content.
        return result
    }

    /** Text content of a comment line: strip leading whitespace + `#` + optional single space. */
    private fun extractContent(line: String, indent: String): String {
        // Strip the indent and `# ` tokens, and return the trimmed
        // contents of the line.
        val afterIndent = line.removePrefix(indent)
        val afterHash = afterIndent.removePrefix("#")
        return if (afterHash.startsWith(" ")) afterHash.substring(1).trimEnd() else afterHash.trimEnd()
    }

    private fun isBlank(line: String, indent: String): Boolean =
        line.removePrefix(indent).removePrefix("#").isBlank()
}