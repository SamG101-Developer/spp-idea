package com.intellij.lang

import com.intellij.lang.Language;

// The S++ language class, which defines the name of the
// language. This is the core class that ties together the
// file type, syntax highlighting, and other language-specific
// features.
public class SppLanguage : Language {
  companion object {
    @JvmStatic
    val INSTANCE: SppLanguage = SppLanguage()
  }

  constructor () : super("S++")
}
