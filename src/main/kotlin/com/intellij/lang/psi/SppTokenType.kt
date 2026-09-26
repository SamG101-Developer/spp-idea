package com.intellij.lang.psi

import com.intellij.lang.SppLanguage
import com.intellij.psi.tree.IElementType

// The S++ token type class, which defines the token types
// for the S++ language. This is used in the lexer and PSI
// tree to represent the different tokens of the language.
class SppTokenType : IElementType {
  constructor(debugName: String) : super(debugName, SppLanguage.INSTANCE)

  override fun toString(): String {
    return "SppTokenType." + super.toString()
  }
}
