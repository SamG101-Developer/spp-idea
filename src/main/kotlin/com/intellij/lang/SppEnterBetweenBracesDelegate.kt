package com.intellij.lang

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesDelegate

// This class is used to determine if the user has pressed
// enter between a pair of braces, and if so, to insert a
// new line and indent the cursor appropriately. All the
// brackets types are considered.
class SppEnterBetweenBracesDelegate : EnterBetweenBracesDelegate() {
  override fun isBracePair(lBrace: Char, rBrace: Char): Boolean {
    return lBrace == '{' && rBrace == '}' || lBrace == '(' && rBrace == ')' || lBrace == '[' && rBrace == ']'
  }
}
