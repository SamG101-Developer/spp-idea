package com.intellij.lang

import com.intellij.lang.psi.SppTypes
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * The `SppBraceMatcher` is used to auto insert a closing brace when an opening one is typed in. There are conditions as
 * to when the closing brace is added; for example it will not add if there are alphanumeric characters directly on the
 * right side of the bracket, as it is assumed that the brackets will be enclosing these characters. If there is a
 * space, newline, comment etc, then it is assumed the closing bracket is wanted.
 */
class SppBraceMatcher : PairedBraceMatcher {
    /**
     * Extract the pairs from the SppTypes object. This is used by the IDE to determine which braces are paired
     * together.
     */
    override fun getPairs(): Array<out BracePair?> {
        // Refer to the statically defined PAIRS array.
        return PAIRS
    }

    /**
     * Only auto add the matching bracket if there is a whitespace or comment next; prevents inserting the matching
     * bracket when there is text right after, as we likely don't want it then (can manually add).
     */
    override fun isPairedBracesAllowedBeforeType(
        leftBraceType: IElementType, ctx: IElementType?
    ): Boolean {
        // Check the following character and determine if the closing bracket is wanted.
        return ctx == null || ctx == TokenType.WHITE_SPACE || ctx == SppTypes.LINE_COMMENT
    }

    /**
     * The offset will just be the opening brace offset, for code folding and other future features that may need the
     * bracket indexing to perform actions.
     */
    override fun getCodeConstructStart(
        file: PsiFile?, openingBraceOffset: Int
    ): Int {
        // Return the opening brace offset.
        return openingBraceOffset
    }
}

private val PAIRS = arrayOf(
    BracePair(SppTypes.TOKEN_LEFT_CURLY_BRACE, SppTypes.TOKEN_RIGHT_CURLY_BRACE, true),
    BracePair(SppTypes.TOKEN_LEFT_PARENTHESIS, SppTypes.TOKEN_RIGHT_PARENTHESIS, false),
    BracePair(SppTypes.TOKEN_LEFT_SQUARE_BRACKET, SppTypes.TOKEN_RIGHT_SQUARE_BRACKET, false)
)
