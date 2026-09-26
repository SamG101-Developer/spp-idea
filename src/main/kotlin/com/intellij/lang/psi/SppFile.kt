package com.intellij.lang.psi

import com.intellij.extapi.psi.PsiFileBase

// The S++ file class, which represents a S++ file in the PSI
// tree. This class is used to provide information about the
// file, such as its type and name, and to provide access to
// the PSI tree for the file.
class SppFile : PsiFileBase {
  constructor(viewProvider: com.intellij.psi.FileViewProvider) : super(
    viewProvider,
    com.intellij.lang.SppLanguage.INSTANCE
  )

  // Returns the file type for this S++ file. This is used to
  // determine how to handle the file, such as which editor to
  // use and which syntax highlighting to apply.
  override fun getFileType(): com.intellij.openapi.fileTypes.FileType {
    return com.intellij.lang.SppFileType.INSTANCE
  }

  // Returns a string representation of this S++ file. This is
  // used for debugging and logging purposes.
  override fun toString(): String {
    return "S++ File"
  }
}
