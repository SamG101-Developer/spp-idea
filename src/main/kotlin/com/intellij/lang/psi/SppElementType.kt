package com.intellij.lang.psi

import com.intellij.lang.SppLanguage
import com.intellij.psi.tree.IElementType

// The S++ element type class, which defines the element types
// for the S++ language. This is used in the parser and PSI
// tree to represent the different elements of the language.
class SppElementType : IElementType {
  constructor(debugName: String) : super(debugName, SppLanguage.INSTANCE)
}