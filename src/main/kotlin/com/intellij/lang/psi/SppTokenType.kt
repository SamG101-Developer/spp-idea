package com.intellij.lang.psi

import com.intellij.lang.SppLanguage
import com.intellij.psi.tree.IElementType

class SppTokenType : IElementType {
    constructor(debugName: String) : super(debugName, SppLanguage.INSTANCE)

    override fun toString(): String {
        return "SppTokenType." + super.toString()
    }
}