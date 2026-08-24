package com.intellij.lang

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider

/**
 * Supplies S++-specific defaults for the code style settings, most notably a default
 * indent/tab width of 2 (rather than the platform default of 4).
 */
class SppCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage() = SppLanguage.INSTANCE

    override fun customizeDefaults(
        commonSettings: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions
    ) {
        indentOptions.INDENT_SIZE = 2
        indentOptions.CONTINUATION_INDENT_SIZE = 4
        indentOptions.TAB_SIZE = 2
    }

    override fun getCodeSample(settingsType: SettingsType): String = ""
}