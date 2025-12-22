package com.intellij.lang

import com.intellij.lang.Language;


public class SppLanguage : Language {
    companion object {
        @JvmStatic
        val INSTANCE: SppLanguage = SppLanguage()
    }

    constructor () : super("S++")
}
