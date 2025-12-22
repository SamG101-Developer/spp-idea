package com.intellij.lang

import com.intellij.lang.psi.SppTokenSets
import com.intellij.lang.psi.SppTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors.BAD_CHARACTER
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType


class SppSyntaxHighlighter : SyntaxHighlighterBase {
    companion object {
        val IDENTIFIER: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val TYPE_IDENTIFIER: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)
        val KEYWORD: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val NUMBER: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_STRING", DefaultLanguageHighlighterColors.STRING)
        val COMMENT: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val OPERATOR: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACKET: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
        val ATTRIBUTE: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_ATTRIBUTE", DefaultLanguageHighlighterColors.IDENTIFIER)
        val ANNOTATION: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("SPP_ANNOTATION", DefaultLanguageHighlighterColors.METADATA)

        val BAD_CHAR_KEYS: Array<TextAttributesKey> = arrayOf(BAD_CHARACTER)
        val EMPTY_KEYS: Array<TextAttributesKey> = arrayOf()
        val IDENTIFIER_KEYS: Array<TextAttributesKey> = arrayOf(IDENTIFIER)
        val TYPE_IDENTIFIER_KEYS: Array<TextAttributesKey> = arrayOf(TYPE_IDENTIFIER)
        val KEYWORD_KEYS: Array<TextAttributesKey> = arrayOf(KEYWORD)
        val NUMBER_KEYS: Array<TextAttributesKey> = arrayOf(NUMBER)
        val STRING_KEYS: Array<TextAttributesKey> = arrayOf(STRING)
        val COMMENT_KEYS: Array<TextAttributesKey> = arrayOf(COMMENT)
        val OPERATOR_KEYS: Array<TextAttributesKey> = arrayOf(OPERATOR)
        val BRACKET_KEYS: Array<TextAttributesKey> = arrayOf(BRACKET)
        val ATTRIBUTE_KEYS: Array<TextAttributesKey> = arrayOf(ATTRIBUTE)
        val ANNOTATION_KEYS: Array<TextAttributesKey> = arrayOf(ANNOTATION)
    }

    constructor() : super()

    override fun getHighlightingLexer(): Lexer {
        return SppLexerAdaptor()
    }

    override fun getTokenHighlights(tokenType: IElementType?): Array<out TextAttributesKey?> {
        return when (tokenType) {
            in SppTokenSets.KEYWORDS -> KEYWORD_KEYS
            in SppTokenSets.NUMBERS -> NUMBER_KEYS
            in SppTokenSets.STRINGS -> STRING_KEYS
            in SppTokenSets.COMMENTS -> COMMENT_KEYS
            in SppTokenSets.OPERATORS -> OPERATOR_KEYS
            in SppTokenSets.BRACKETS -> BRACKET_KEYS
            SppTypes.LEXEME_IDENTIFIER -> IDENTIFIER_KEYS
            SppTypes.LEXEME_UPPER_IDENTIFIER -> TYPE_IDENTIFIER_KEYS
            else -> EMPTY_KEYS
        }
    }
}