package com.intellij.lang

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.lang.psi.SppTypes

/**
 * The `SppQuoteHandler` is used to auto insert a closing quote when an opening one is typed in, mirroring the
 * bracket auto-closing behaviour from [SppBraceMatcher]. Both the bare quote tokens (`'`, `"`), emitted by the
 * lexer when a quote has not yet been closed, and the fully lexed string/char literal tokens (which include their
 * surrounding quotes) are registered, so typing is handled correctly whether the literal is open or already closed.
 */
class SppQuoteHandler : SimpleTokenSetQuoteHandler(
    SppTypes.TOKEN_SINGLE_QUOTE,
    SppTypes.TOKEN_DOUBLE_QUOTE,
    SppTypes.LEXEME_SINGLE_QUOTE_STR,
    SppTypes.LEXEME_DOUBLE_QUOTE_STR
)
