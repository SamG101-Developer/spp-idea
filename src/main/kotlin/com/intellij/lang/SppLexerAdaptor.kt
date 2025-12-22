package com.intellij.lang

import com.intellij.lexer.FlexAdapter

class SppLexerAdaptor : FlexAdapter {
    constructor() : super(_SppLexer(null))
}