package com.intellij.lang

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.psi.SppTypes
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

// Ctrl+clicking a bracket jumps to the one that matches it:
// an opening brace goes forward to its closing brace, and a
// closing brace goes back to its opening one. Only brackets
// of the same kind are counted, so a stray bracket of another
// kind in between does not stop the search.
class SppBraceGotoHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor,
  ): Array<PsiElement>? {
    val brace = sourceElement ?: return null
    if (brace.language != SppLanguage.INSTANCE) return null
    return matchingBrace(brace)?.let { arrayOf(it) }
  }

}

private val OPENING_TO_CLOSING = mapOf(
  SppTypes.TOKEN_LEFT_CURLY_BRACE to SppTypes.TOKEN_RIGHT_CURLY_BRACE,
  SppTypes.TOKEN_LEFT_PARENTHESIS to SppTypes.TOKEN_RIGHT_PARENTHESIS,
  SppTypes.TOKEN_LEFT_SQUARE_BRACKET to SppTypes.TOKEN_RIGHT_SQUARE_BRACKET,
)

private val CLOSING_TO_OPENING = OPENING_TO_CLOSING.entries.associate { (open, close) -> close to open }

// Find the bracket that matches the given one, or null if the
// element is not a bracket, or if it is unbalanced.
fun matchingBrace(brace: PsiElement): PsiElement? {
  val type = brace.elementType ?: return null
  OPENING_TO_CLOSING[type]?.let { closing -> return walk(brace, type, closing, forward = true) }
  CLOSING_TO_OPENING[type]?.let { opening -> return walk(brace, type, opening, forward = false) }
  return null
}

// Move leaf by leaf away from the starting bracket, counting
// nested brackets of the same kind, until the depth returns
// to zero at the matching bracket.
private fun walk(start: PsiElement, same: IElementType, other: IElementType, forward: Boolean): PsiElement? {
  var depth = 1
  var leaf = next(start, forward)
  while (leaf != null) {
    when (leaf.elementType) {
      same -> depth++
      other -> if (--depth == 0) return leaf
    }
    leaf = next(leaf, forward)
  }
  return null
}

private fun next(leaf: PsiElement, forward: Boolean): PsiElement? =
  if (forward) PsiTreeUtil.nextLeaf(leaf) else PsiTreeUtil.prevLeaf(leaf)