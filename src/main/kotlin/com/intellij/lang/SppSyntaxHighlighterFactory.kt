package com.intellij.lang

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

// The S++ syntax highlighter factory class, which is used
// to create instances of the S++ syntax highlighter.
public class SppSyntaxHighlighterFactory : SyntaxHighlighterFactory {
  constructor() : super()

  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
    return SppSyntaxHighlighter()
  }
}
