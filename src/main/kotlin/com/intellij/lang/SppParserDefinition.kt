package com.intellij.lang

import com.intellij.lang.psi.SppFile
import com.intellij.lang.psi.SppTokenSets
import com.intellij.lang.psi.SppTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

// The S++ parser definition class, which defines how to parse
// S++ files and create PSI elements. This class is responsible
// for creating the lexer, parser, and PSI elements for S++ files.
class SppParserDefinition : ParserDefinition {
  companion object {
    @JvmStatic
    val FILE = IFileElementType(SppLanguage.INSTANCE)
  }

  override fun createLexer(project: Project?): Lexer {
    return SppLexerAdaptor()
  }

  override fun getCommentTokens(): TokenSet {
    return SppTokenSets.COMMENTS
  }

  override fun getStringLiteralElements(): TokenSet {
    return SppTokenSets.STRINGS
  }

  override fun createParser(project: Project?): PsiParser {
    return SppParser()
  }

  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return SppFile(viewProvider)
  }

  override fun createElement(node: ASTNode?): PsiElement {
    return SppTypes.Factory.createElement(node)
  }
}