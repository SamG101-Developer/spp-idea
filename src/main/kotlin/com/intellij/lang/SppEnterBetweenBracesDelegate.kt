package com.intellij.lang

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesDelegate

class SppEnterBetweenBracesDelegate : EnterBetweenBracesDelegate() {
    override fun isBracePair(lBrace: Char, rBrace: Char): Boolean {
        return lBrace == '{' && rBrace == '}' || lBrace == '(' && rBrace == ')' || lBrace == '[' && rBrace == ']'
    }
}