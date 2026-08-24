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

class SppFoldingBuilder : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()

        // Every `{ ... }` bearing construct: module top level, function/class/sup bodies,
        // and inner_scope_expression (loop/case/closure/anonymous-block bodies, and any bare block).
        val blocks = PsiTreeUtil.findChildrenOfAnyType(
            root,
            SppModuleImplementation::class.java,
            SppFunctionImplementation::class.java,
            SppClassImplementation::class.java,
            SppSupImplementation::class.java,
            SppInnerScopeExpression::class.java
        )

        for (block in blocks) {
            // The module's implicit top-level block has no braces of its own to fold.
            if (block !is SppModuleImplementation) {
                descriptors.add(FoldingDescriptor(block.node, block.textRange))
            }
            addUseStatementRuns(block, descriptors)
        }

        return descriptors.toTypedArray()
    }

    private fun addUseStatementRuns(block: PsiElement, descriptors: MutableList<FoldingDescriptor>) {
        val children = block.children.toList()

        var i = 0
        while (i < children.size) {
            val child = children[i]
            if (!child.isUseStatement()) {
                i++; continue
            }

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

            if (runEnd !== runStart) {
                val range = TextRange(runStart.textRange.startOffset, runEnd.textRange.endOffset)
                descriptors.add(FoldingDescriptor(runStart.node, range))
            }
            i = j
        }
    }

    override fun getPlaceholderText(node: ASTNode): String =
        if (node.psi.isUseStatement()) "use ..." else "{...}"

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    // module_member/function_member/statement are transparent alternation wrappers (no
    // surrounding syntax of their own), so their text range always matches whichever
    // alternative matched - safe to treat the wrapper itself as the foldable element.
    private fun PsiElement.isUseStatement(): Boolean = when (this) {
        is SppModuleMember -> globalUseStatement != null || globalUseVarStatement != null
        is SppFunctionMember -> statement.isUseStatement()
        is SppStatement -> useStatement != null || useVarStatement != null
        else -> false
    }
}