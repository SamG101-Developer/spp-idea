package com.intellij.lang

import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.psi.SppClassImplementation
import com.intellij.lang.psi.SppFunctionImplementation
import com.intellij.lang.psi.SppFunctionMember
import com.intellij.lang.psi.SppInnerScopeExpression
import com.intellij.lang.psi.SppModuleImplementation
import com.intellij.lang.psi.SppModuleMember
import com.intellij.lang.psi.SppStatement
import com.intellij.lang.psi.SppSupImplementation
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

// This class provides all the folding regions for different
// blocks of code, such as consecutive "use" statements, or
// brace enclosed blocks.
class SppFoldingBuilder : FoldingBuilderEx() {

  // Provide the foldable regions via their element classes,
  // and add the "use" statement runs as well.
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    val descriptors = mutableListOf<FoldingDescriptor>()

    // Every `{ ... }` bearing construct: module top level,
    // function/class/sup bodies, and inner_scope_expression
    // (loop/case/closure/anonymous-block bodies, and any
    // bare block).
    val blocks = PsiTreeUtil.findChildrenOfAnyType(
      root,
      SppModuleImplementation::class.java,
      SppFunctionImplementation::class.java,
      SppClassImplementation::class.java,
      SppSupImplementation::class.java,
      SppInnerScopeExpression::class.java
    )

    for (block in blocks) {
      // The module's implicit top-level block has no braces of
      // its own to fold.
      if (block !is SppModuleImplementation) {
        descriptors.add(FoldingDescriptor(block.node, block.textRange))
      }
      addUseStatementRuns(block, descriptors)
    }

    return descriptors.toTypedArray()
  }

  // Detect and group the "use" statements into runs, and add a
  // folding descriptor for each run.
  private fun addUseStatementRuns(block: PsiElement, descriptors: MutableList<FoldingDescriptor>) {
    val children = block.children.toList()

    var i = 0
    while (i < children.size) {

      // Search upto the first "use" statement, skipping all non-
      // "use" top-level statements in the element.
      val child = children[i]
      if (!child.isUseStatement()) {
        i++; continue
      }

      // Skip until we find either a non-"use" statement ast, or
      // a blank line that might be splitting groups of "use"
      // statements.
      val runStart = child
      var runEnd = child
      var j = i + 1
      while (j < children.size) {
        val next = children[j]
        when {
          next.isUseStatement() -> {
            runEnd = next; j++
          }

          next is PsiWhiteSpace && !next.text.contains("\n\n") -> j++
          else -> break
        }
      }

      // Given we have a non-zero range, create the "text range"
      // for the run, and add a folding descriptor for it.
      if (runEnd !== runStart) {
        val range = TextRange(runStart.textRange.startOffset, runEnd.textRange.endOffset)
        descriptors.add(FoldingDescriptor(runStart.node, range))
      }
      i = j
    }
  }

  // Create the text that shows in the folding region collapsed
  // header. Customized for "use" statements. Todo: Possibly
  // customize per region. Use parent element.
  override fun getPlaceholderText(node: ASTNode): String =
    if (node.psi.isUseStatement()) "use ..." else "{...}"

  // No elements get collapsed by default.
  override fun isCollapsedByDefault(node: ASTNode): Boolean = false

  // Wrapper to detect if an ast element is a "use" statement,
  // either directly or indirectly.
  private fun PsiElement.isUseStatement(): Boolean = when (this) {
    is SppModuleMember -> globalUseStatement != null || globalUseVarStatement != null
    is SppFunctionMember -> statement.isUseStatement()
    is SppStatement -> useStatement != null || useVarStatement != null
    else -> false
  }
}