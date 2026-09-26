package com.intellij.lang

import com.intellij.lang.psi.SppTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor

// This class is responsible for sorting "use" statements
// within their groups, sorting alphabetically, sorting by
// depth of path, and by type of use (var vs type).
class SppImportSorter : PostFormatProcessor {
  // The steps to "process an element" are to just return
  // it.
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

  // The steps to "process text" are to sort "use" statements
  // in the source file, if it is an S++ file. The sorting is
  // done in a write command action, so that it can be undone
  // or redone.
  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (source.language != SppLanguage.INSTANCE) return rangeToReformat
    val project = source.project
    val doc = PsiDocumentManager.getInstance(project).getDocument(source) ?: return rangeToReformat

    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      WriteCommandAction.runWriteCommandAction(project) {
        if (!source.isValid) return@runWriteCommandAction
        PsiDocumentManager.getInstance(project).commitDocument(doc)
        sortUseGroupsInFile(source, doc)
      }
    }
    return rangeToReformat
  }

  // Sort the "use" statement groups in the file, replacing them
  // in the document with their sorted versions.
  private fun sortUseGroupsInFile(file: PsiFile, doc: Document) {
    val moduleImpl = findModuleImpl(file.node) ?: return
    val groups = findUseGroups(moduleImpl)

    val replacements = mutableListOf<Triple<Int, Int, String>>()
    for (group in groups) {
      val sorted = group.sortedWith(USE_COMPARATOR)
      if (sorted.map { it.text } == group.map { it.text }) continue
      for ((original, replacement) in group.zip(sorted)) {
        replacements.add(
          Triple(
            original.startOffset,
            original.startOffset + original.textLength,
            replacement.text
          )
        )
      }
    }

    // Apply from end to start so earlier offsets stay valid.
    replacements.sortByDescending { it.first }
    for ((start, end, newText) in replacements) {
      doc.replaceString(start, end, newText)
    }
  }

  // Find the module implementation node in the AST, which is
  // the root of the module's top-level code. This is where
  // "use" statements are located.
  private fun findModuleImpl(root: ASTNode): ASTNode? {
    if (root.elementType == SppTypes.MODULE_IMPLEMENTATION) return root
    var child = root.firstChildNode
    while (child != null) {
      val found = findModuleImpl(child)
      if (found != null) return found
      child = child.treeNext
    }
    return null
  }

  // Find the groups of "use" statements in the discovered module
  // implementation.
  private fun findUseGroups(moduleImpl: ASTNode): List<List<ASTNode>> {
    val groups = mutableListOf<List<ASTNode>>()
    var currentGroup = mutableListOf<ASTNode>()
    var prevWasUse = false
    var child = moduleImpl.firstChildNode

    while (child != null) {
      val type = child.elementType

      if (type == TokenType.WHITE_SPACE) {
        if (prevWasUse && child.text.count { it == '\n' } >= 2) {
          if (currentGroup.size > 1) groups.add(currentGroup.toList())
          currentGroup = mutableListOf()
          prevWasUse = false
        }
        child = child.treeNext
        continue
      }

      if (type != SppTypes.MODULE_MEMBER) {
        if (currentGroup.size > 1) groups.add(currentGroup.toList())
        currentGroup = mutableListOf()
        prevWasUse = false
        child = child.treeNext
        continue
      }

      val innerType = child.firstChildNode?.elementType
      val isUse = innerType == SppTypes.GLOBAL_USE_STATEMENT ||
          innerType == SppTypes.GLOBAL_USE_VAR_STATEMENT

      if (!isUse) {
        if (currentGroup.size > 1) groups.add(currentGroup.toList())
        currentGroup = mutableListOf()
        prevWasUse = false
        child = child.treeNext
        continue
      }

      currentGroup.add(child)
      prevWasUse = true
      child = child.treeNext
    }

    if (currentGroup.size > 1) groups.add(currentGroup.toList())
    return groups
  }

  // The companion object contains utility functions for sorting
  // "use" statements, including extracting the path, counting
  // the number of "::" separators, determining the type of use
  // statement, and comparing two use statements for sorting.
  companion object {
    private fun usePath(member: ASTNode): String {
      val text = member.text
      val idx = text.indexOf("use ")
      return if (idx >= 0) text.substring(idx + 4).trim() else text.trim()
    }

    // Count of :: separators in the path.
    private fun colonColonCount(member: ASTNode): Int =
      usePath(member).split("::").size - 1

    // Get the tag for ordering: use_var = 0 (sorts first),
    // use_type = 1 (sorts second).
    private fun useVarOrdinal(member: ASTNode): Int =
      if (member.firstChildNode?.elementType == SppTypes.GLOBAL_USE_VAR_STATEMENT) 0 else 1

    // Split the path into segments for comparison, using "::"
    // as the separator.
    private fun segments(member: ASTNode): List<String> =
      usePath(member).split("::")

    // The comparator for sorting "use" statements, which compares
    // by the number of "::" separators, then by the type of use
    // statement (var vs type), and finally by the segments of the
    // path in lexicographical order.
    private val USE_COMPARATOR = Comparator<ASTNode> { a, b ->
      val countCmp = colonColonCount(a) - colonColonCount(b)
      if (countCmp != 0) return@Comparator countCmp

      val varCmp = useVarOrdinal(a) - useVarOrdinal(b)
      if (varCmp != 0) return@Comparator varCmp

      val segsA = segments(a)
      val segsB = segments(b)
      for (i in 0 until minOf(segsA.size, segsB.size)) {
        val cmp = segsA[i].compareTo(segsB[i], ignoreCase = true)
        if (cmp != 0) return@Comparator cmp
      }
      segsA.size - segsB.size
    }
  }
}