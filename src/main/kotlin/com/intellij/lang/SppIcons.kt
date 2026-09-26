package com.intellij.lang

import com.intellij.openapi.util.IconLoader

// The S++ icons class, which defines the icon for S++ files.
class SppIcons {
  companion object {
    @JvmStatic
    val FILE = IconLoader.getIcon("/icons/logo-tiny-2.svg", SppLanguage::class.java)
  }
}