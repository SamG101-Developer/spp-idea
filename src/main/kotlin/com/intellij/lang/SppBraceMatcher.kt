package com.intellij.lang

import com.intellij.lang.psi.SppTypes
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

// The brace matcher inserts matching parentheses, brackets
// or braces after the left one has been typed. It will not
// be added if the immediately following token is alphanumeric,
// ie we are typing in the middle of a line.
class SppBraceMatcher : PairedBraceMatcher {
  // Implement the "pairs" getter function by returning the
  // statically defined PAIRS array. Add to the array for any
  // new pairs of braces that are added to the language.
  override fun getPairs(): Array<out BracePair?> {
    return PAIRS
  }

  // The filter for if we add the enclosing brace or not. If
  // the next token is whitespace/newline or a comment, we will
  // add it; otherwise, we will not. Not in the middle of a
  // line or comment.
  override fun isPairedBracesAllowedBeforeType(leftBraceType: IElementType, ctx: IElementType?): Boolean {
    return ctx == null || ctx == TokenType.WHITE_SPACE || ctx == SppTypes.LINE_COMMENT
  }

  // The offset will be the opening brace offset, for code
  // folding and other future features that may need the bracket
  // indexing to perform actions.
  override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
    return openingBraceOffset
  }
}

// The pairs of braces that are supported by the language. The
// third argument is if the brace pair is structural, ie it can
// be used for code folding. Only the curly braces are structural.
private val PAIRS = arrayOf(
  BracePair(SppTypes.TOKEN_LEFT_CURLY_BRACE, SppTypes.TOKEN_RIGHT_CURLY_BRACE, true),
  BracePair(SppTypes.TOKEN_LEFT_PARENTHESIS, SppTypes.TOKEN_RIGHT_PARENTHESIS, false),
  BracePair(SppTypes.TOKEN_LEFT_SQUARE_BRACKET, SppTypes.TOKEN_RIGHT_SQUARE_BRACKET, false)
)
