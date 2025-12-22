package com.intellij.lang.psi

import com.intellij.lang.SppLanguage
import com.intellij.psi.tree.IElementType

class SppElementType : IElementType {
    constructor(debugName: String) : super(debugName, SppLanguage.INSTANCE)
}