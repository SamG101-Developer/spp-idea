package com.intellij.lang


import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

public class SppFileType : LanguageFileType {
    companion object {
        @JvmStatic
        val INSTANCE = SppFileType()
    }

    private constructor() : super(SppLanguage.INSTANCE)

    override fun getName(): String {
        return "S++ File"
    }

    override fun getDescription(): String {
        return "S++ language file"
    }

    override fun getDefaultExtension(): String {
        return "spp"
    }

    override fun getIcon(): Icon {
        return SppIcons.FILE
    }
}