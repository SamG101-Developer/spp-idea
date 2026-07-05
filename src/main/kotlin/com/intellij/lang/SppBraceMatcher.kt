package com.intellij.lang

import com.intellij.lang.psi.SppTypes
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class SppBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<out BracePair?> {
        return PAIRS
    }

    override fun isPairedBracesAllowedBeforeType(
        leftBraceType: IElementType,
        ctx: IElementType?
    ): Boolean {
        // Only auto add the matching bracket if there is a whitespace or comment next; prevents
        // inserting the matching bracket when there is text right after, as we likely don't want
        // it then (can manually add).
        return ctx == null || ctx == TokenType.WHITE_SPACE || ctx == SppTypes.LINE_COMMENT
    }

    override fun getCodeConstructStart(
        file: PsiFile?, openingBraceOffset: Int
    ): Int {
        // For now, just return the opening brace offset. This is used for code folding and other
        // features.
        return openingBraceOffset
    }
}

private val PAIRS = arrayOf(
    BracePair(SppTypes.TOKEN_LEFT_CURLY_BRACE, SppTypes.TOKEN_RIGHT_CURLY_BRACE, true),
    BracePair(SppTypes.TOKEN_LEFT_PARENTHESIS, SppTypes.TOKEN_RIGHT_PARENTHESIS, false),
    BracePair(SppTypes.TOKEN_LEFT_SQUARE_BRACKET, SppTypes.TOKEN_RIGHT_SQUARE_BRACKET, false)
)
