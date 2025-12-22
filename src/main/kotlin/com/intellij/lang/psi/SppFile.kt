package com.intellij.lang.psi

import com.intellij.extapi.psi.PsiFileBase

class SppFile : PsiFileBase {
    constructor(viewProvider: com.intellij.psi.FileViewProvider) : super(
        viewProvider,
        com.intellij.lang.SppLanguage.INSTANCE
    )

    override fun getFileType(): com.intellij.openapi.fileTypes.FileType {
        return com.intellij.lang.SppFileType.INSTANCE
    }

    override fun toString(): String {
        return "S++ File"
    }
}