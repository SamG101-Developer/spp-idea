package com.intellij.lang

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider

// The default style provider for S++ code style settings,
// providing defaults for indentation and tab size. The S++
// style defaults to 2 spaces for indentation, 4 spaces for
// continuation indentation, 2 spaces for tab size, and an
// 80-column hard-wrap limit (right margin).
class SppCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage() = SppLanguage.INSTANCE

  // Set key options on the indent options for the language.
  // Todo: Any common settings can be set here too.
  override fun customizeDefaults(
    commonSettings: CommonCodeStyleSettings,
    indentOptions: CommonCodeStyleSettings.IndentOptions
  ) {
    indentOptions.INDENT_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 4
    indentOptions.TAB_SIZE = 2
    commonSettings.RIGHT_MARGIN = 80
  }

  // The code sample to show in the settings dialog.
  // Todo: implement this (store in .spp file and read
  // it in here).
  override fun getCodeSample(settingsType: SettingsType): String = ""
}
