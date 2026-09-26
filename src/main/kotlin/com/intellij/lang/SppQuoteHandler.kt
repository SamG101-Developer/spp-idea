package com.intellij.lang

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.lang.psi.SppTypes

// Same as for parentheses, but for quotes. This class provides
// the tokens that are used for single and double quotes, which
// allows for auto-insertion too.
class SppQuoteHandler : SimpleTokenSetQuoteHandler(
  SppTypes.TOKEN_SINGLE_QUOTE,
  SppTypes.TOKEN_DOUBLE_QUOTE,
  SppTypes.LEXEME_SINGLE_QUOTE_STR,
  SppTypes.LEXEME_DOUBLE_QUOTE_STR
)
