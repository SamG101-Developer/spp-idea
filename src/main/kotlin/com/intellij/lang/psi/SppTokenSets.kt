package com.intellij.lang.psi

import com.intellij.psi.tree.TokenSet

interface SppTokenSets {
    companion object {
        val IDENTIFIERS: TokenSet = TokenSet.create(
            SppTypes.IDENTIFIER, SppTypes.UPPER_IDENTIFIER
        )

        val COMMENTS: TokenSet = TokenSet.create(
            SppTypes.LINE_COMMENT, SppTypes.BLOCK_COMMENT
        )

        val STRINGS: TokenSet = TokenSet.create(
            SppTypes.LITERAL_STRING, SppTypes.LITERAL_CHAR
        )

        val NUMBERS: TokenSet = TokenSet.create(
            SppTypes.LEXEME_DEC_DIGITS,
            SppTypes.LEXEME_HEX_DIGITS,
            SppTypes.LEXEME_BIN_DIGITS,
            SppTypes.LEXEME_OCT_DIGITS
        )

        val KEYWORDS: TokenSet = TokenSet.create(
            SppTypes.KEYWORD_CLS,
            SppTypes.KEYWORD_FUN,
            SppTypes.KEYWORD_COR,
            SppTypes.KEYWORD_SUP,
            SppTypes.KEYWORD_EXT,
            SppTypes.KEYWORD_MUT,
            SppTypes.KEYWORD_USE,
            SppTypes.KEYWORD_CMP,
            SppTypes.KEYWORD_LET,
            SppTypes.KEYWORD_TYPE,
            SppTypes.KEYWORD_SELF,
            SppTypes.KEYWORD_CASE,
            SppTypes.KEYWORD_ITER,
            SppTypes.KEYWORD_OF,
            SppTypes.KEYWORD_LOOP,
            SppTypes.KEYWORD_IN,
            SppTypes.KEYWORD_ELSE,
            SppTypes.KEYWORD_GEN,
            SppTypes.KEYWORD_WITH,
            SppTypes.KEYWORD_RET,
            SppTypes.KEYWORD_EXIT,
            SppTypes.KEYWORD_SKIP,
            SppTypes.KEYWORD_IS,
            SppTypes.KEYWORD_AS,
            SppTypes.KEYWORD_OR,
            SppTypes.KEYWORD_AND,
            SppTypes.KEYWORD_NOT,
            SppTypes.KEYWORD_ASYNC,
            SppTypes.KEYWORD_TRUE,
            SppTypes.KEYWORD_FALSE,
            SppTypes.KEYWORD_RES,
            SppTypes.KEYWORD_CAPS
        )

        val OPERATORS: TokenSet = TokenSet.create(
            SppTypes.TOKEN_ADD,
            SppTypes.TOKEN_SUB,
            SppTypes.TOKEN_MUL,
            SppTypes.TOKEN_DIV,
            SppTypes.TOKEN_REM,
            SppTypes.TOKEN_POW,
            SppTypes.TOKEN_BIT_IOR,
            SppTypes.TOKEN_BIT_AND,
            SppTypes.TOKEN_BIT_XOR,
            SppTypes.TOKEN_BIT_SHL,
            SppTypes.TOKEN_BIT_SHR,
            SppTypes.TOKEN_ASSIGN,
            SppTypes.TOKEN_ADD_ASSIGN,
            SppTypes.TOKEN_SUB_ASSIGN,
            SppTypes.TOKEN_MUL_ASSIGN,
            SppTypes.TOKEN_DIV_ASSIGN,
            SppTypes.TOKEN_REM_ASSIGN,
            SppTypes.TOKEN_POW_ASSIGN,
            SppTypes.TOKEN_BIT_IOR_ASSIGN,
            SppTypes.TOKEN_BIT_AND_ASSIGN,
            SppTypes.TOKEN_BIT_XOR_ASSIGN,
            SppTypes.TOKEN_BIT_SHL_ASSIGN,
            SppTypes.TOKEN_BIT_SHR_ASSIGN,
            SppTypes.TOKEN_EQUALS,
            SppTypes.TOKEN_NOT_EQUALS,
            SppTypes.TOKEN_LESS_THAN,
            SppTypes.TOKEN_LESS_THAN_EQUALS,
            SppTypes.TOKEN_GREATER_THAN,
            SppTypes.TOKEN_GREATER_THAN_EQUALS,
            SppTypes.TOKEN_DOT,
            SppTypes.TOKEN_DOUBLE_COLON,
            SppTypes.TOKEN_QUESTION_MARK,
            SppTypes.TOKEN_EXCLAMATION_MARK,
            SppTypes.TOKEN_DOUBLE_EXCLAMATION_MARK,
        )

        val BRACKETS: TokenSet = TokenSet.create(
            SppTypes.TOKEN_LEFT_PARENTHESIS,
            SppTypes.TOKEN_RIGHT_PARENTHESIS,
            SppTypes.TOKEN_LEFT_CURLY_BRACE,
            SppTypes.TOKEN_RIGHT_CURLY_BRACE,
            SppTypes.TOKEN_LEFT_SQUARE_BRACKET,
            SppTypes.TOKEN_RIGHT_SQUARE_BRACKET
        )
    }
}