package com.intellij.lang

import com.intellij.lexer.FlexAdapter

// The S++ lexer adaptor class, which is used to adapt the
// S++ lexer to the IntelliJ platform.
class SppLexerAdaptor : FlexAdapter {
    constructor() : super(_SppLexer(null))
}
