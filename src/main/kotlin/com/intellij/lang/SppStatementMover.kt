package com.intellij.lang

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.lang.psi.SppAnnotation
import com.intellij.lang.psi.SppClassMember
import com.intellij.lang.psi.SppFile
import com.intellij.lang.psi.SppFunctionMember
import com.intellij.lang.psi.SppModuleMember
import com.intellij.lang.psi.SppSupMember
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

// Move Statement Up/Down (ctrl+shift+up/down) for S++. When the
// caret is on the header line of a multi-line member (fun, cls,
// sup, case, loop, ...), the whole member moves past its
// neighbouring sibling member as one unit. Anywhere else this
// mover declines and the platform's line mover takes over.
class SppStatementMover : StatementUpDownMover() {

  override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
    if (file !is SppFile) return false
    val document = editor.document

    // Only act on a caret (or a selection) confined to one line.
    val selection = editor.selectionModel
    val caretLine = editor.caretModel.logicalPosition.line
    if (selection.hasSelection() &&
      document.getLineNumber(selection.selectionStart) != document.getLineNumber(selection.selectionEnd)
    ) return false

    val member = blockMemberOnHeaderLine(file, document, caretLine) ?: return false
    if (!standsOnOwnLines(member, document)) return false

    val startLine = attachedCommentsStart(document, document.getLineNumber(member.textRange.startOffset))
    val endLine = document.getLineNumber(member.textRange.endOffset)
    info.toMove = LineRange(startLine, endLine + 1)

    if (down) {
      val next = endLine + 1
      if (next >= document.lineCount) return info.prohibitMove()

      // Step over blank lines one at a time, like the line mover.
      if (isBlank(document, next)) {
        info.toMove2 = LineRange(next, next + 1)
        return true
      }

      val sibling = siblingMember(member, forward = true)
      if (sibling == null || !standsOnOwnLines(sibling, document) ||
        attachedCommentsStart(document, document.getLineNumber(sibling.textRange.startOffset)) != next
      ) return info.prohibitMove()

      info.toMove2 = LineRange(next, document.getLineNumber(sibling.textRange.endOffset) + 1)
    } else {
      val prev = startLine - 1
      if (prev < 0) return info.prohibitMove()

      if (isBlank(document, prev)) {
        info.toMove2 = LineRange(prev, prev + 1)
        return true
      }

      val sibling = siblingMember(member, forward = false)
      if (sibling == null || !standsOnOwnLines(sibling, document) ||
        document.getLineNumber(sibling.textRange.endOffset) != prev
      ) return info.prohibitMove()

      val siblingStart = attachedCommentsStart(document, document.getLineNumber(sibling.textRange.startOffset))
      info.toMove2 = LineRange(siblingStart, prev + 1)
    }
    return true
  }

  // The innermost member element whose header (annotations plus
  // the line holding its first real token) covers the caret line,
  // and which continues onto later lines, i.e. a block.
  private fun blockMemberOnHeaderLine(file: PsiFile, document: Document, line: Int): PsiElement? {
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val text = document.charsSequence
    var offset = lineStart
    while (offset < lineEnd && text[offset].isWhitespace()) offset++
    if (offset >= lineEnd) return null

    var element: PsiElement? = file.findElementAt(offset)
    while (element != null && element !is PsiFile) {
      if (element.isMember()) {
        val startLine = document.getLineNumber(element.textRange.startOffset)
        val headerLine = document.getLineNumber(firstNonAnnotationLeaf(element)?.textRange?.startOffset ?: return null)
        val endLine = document.getLineNumber(element.textRange.endOffset)
        if (line in startLine..headerLine) return if (endLine > headerLine) element else null
      }
      element = element.parent
    }
    return null
  }

  private fun PsiElement.isMember(): Boolean =
    this is SppModuleMember || this is SppSupMember || this is SppFunctionMember || this is SppClassMember

  private fun firstNonAnnotationLeaf(member: PsiElement): PsiElement? {
    var leaf: PsiElement? = PsiTreeUtil.firstChild(member)
    val end = member.textRange.endOffset
    while (leaf != null && leaf.textRange.startOffset < end) {
      if (leaf !is PsiWhiteSpace && leaf !is PsiComment &&
        PsiTreeUtil.getParentOfType(leaf, SppAnnotation::class.java, false, member::class.java) == null
      ) return leaf
      leaf = PsiTreeUtil.nextLeaf(leaf)
    }
    return null
  }

  private fun siblingMember(member: PsiElement, forward: Boolean): PsiElement? {
    val sibling = if (forward)
      PsiTreeUtil.skipWhitespacesAndCommentsForward(member)
    else
      PsiTreeUtil.skipWhitespacesAndCommentsBackward(member)
    return sibling?.takeIf { it.isMember() }
  }

  // The member is alone on its first and last lines (comments
  // after it are fine), so whole-line moves don't tear code.
  private fun standsOnOwnLines(element: PsiElement, document: Document): Boolean {
    val text = document.charsSequence
    val range = element.textRange
    val startLine = document.getLineNumber(range.startOffset)
    val endLine = document.getLineNumber(range.endOffset)
    val before = text.subSequence(document.getLineStartOffset(startLine), range.startOffset)
    val after = text.subSequence(range.endOffset, document.getLineEndOffset(endLine)).trim()
    return before.isBlank() && (after.isEmpty() || after.startsWith("#"))
  }

  // Comment lines directly above a member travel with it.
  private fun attachedCommentsStart(document: Document, line: Int): Int {
    var start = line
    while (start > 0 && lineText(document, start - 1).trimStart().startsWith("#")) start--
    return start
  }

  private fun isBlank(document: Document, line: Int) = lineText(document, line).isBlank()

  private fun lineText(document: Document, line: Int): CharSequence =
    document.charsSequence.subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line))
}
