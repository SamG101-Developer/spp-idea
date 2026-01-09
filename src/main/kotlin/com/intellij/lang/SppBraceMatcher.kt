package com.intellij.lang

import com.intellij.lang.psi.SppTypes
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType


class SppBraceMatcher : PairedBraceMatcher {
    companion object {
        private val PAIRS = arrayOf(
            BracePair(SppTypes.TOKEN_LEFT_CURLY_BRACE, SppTypes.TOKEN_RIGHT_CURLY_BRACE, true),
            BracePair(SppTypes.TOKEN_LEFT_PARENTHESIS, SppTypes.TOKEN_RIGHT_PARENTHESIS, false),
            BracePair(SppTypes.TOKEN_LEFT_SQUARE_BRACKET, SppTypes.TOKEN_RIGHT_SQUARE_BRACKET, false)
        )
    }

    override fun getPairs(): Array<out BracePair?> {
        return PAIRS
    }

    override fun isPairedBracesAllowedBeforeType(
        leftBraceType: IElementType,
        ctx: IElementType?
    ): Boolean {
        return true
    }

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }
}
