package com.intellij.lang

import com.intellij.formatting.*
import com.intellij.lang.psi.SppTypes
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock

private val BRACE_BLOCK_TYPES = setOf(
    SppTypes.FUNCTION_IMPLEMENTATION,
    SppTypes.CLASS_IMPLEMENTATION,
    SppTypes.SUP_IMPLEMENTATION,
    SppTypes.INNER_SCOPE_EXPRESSION,
    SppTypes.CASE_OF_EXPRESSION,
)

class SppFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(context: FormattingContext): FormattingModel {
        val root = SppBlock(context.node, Indent.getNoneIndent(), context.codeStyleSettings)
        return FormattingModelProvider.createFormattingModelForPsiFile(
            context.containingFile, root, context.codeStyleSettings
        )
    }
}

class SppBlock(
    node: ASTNode,
    private val indent: Indent,
    private val settings: CodeStyleSettings,
) : AbstractBlock(node, null, null) {

    override fun getIndent(): Indent = indent

    override fun buildChildren(): List<Block> {
        val blocks = mutableListOf<Block>()
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE) {
                blocks.add(SppBlock(child, childIndentFor(child), settings))
            }
            child = child.treeNext
        }
        return blocks
    }

    private fun childIndentFor(child: ASTNode): Indent {
        if (node.elementType in BRACE_BLOCK_TYPES) {
            val childType = child.elementType
            if (childType == SppTypes.TOKEN_LEFT_CURLY_BRACE || childType == SppTypes.TOKEN_RIGHT_CURLY_BRACE)
                return Indent.getNoneIndent()

            // case_of_expression has a header (case expr of) before its {; only indent the branches
            if (node.elementType == SppTypes.CASE_OF_EXPRESSION) {
                val lBrace = node.findChildByType(SppTypes.TOKEN_LEFT_CURLY_BRACE) ?: return Indent.getNoneIndent()
                return if (child.startOffset > lBrace.startOffset) Indent.getNormalIndent() else Indent.getNoneIndent()
            }

            return Indent.getNormalIndent()
        }

        val lParen = node.findChildByType(SppTypes.TOKEN_LEFT_PARENTHESIS)
        if (lParen != null) {
            val childType = child.elementType
            if (childType == SppTypes.TOKEN_LEFT_PARENTHESIS || childType == SppTypes.TOKEN_RIGHT_PARENTHESIS)
                return Indent.getNoneIndent()
            val rParen = node.findChildByType(SppTypes.TOKEN_RIGHT_PARENTHESIS)
            if (child.startOffset > lParen.startOffset &&
                (rParen == null || child.startOffset < rParen.startOffset))
                return Indent.getNormalIndent()
        }

        val lBracket = node.findChildByType(SppTypes.TOKEN_LEFT_SQUARE_BRACKET)
        if (lBracket != null) {
            val childType = child.elementType
            if (childType == SppTypes.TOKEN_LEFT_SQUARE_BRACKET || childType == SppTypes.TOKEN_RIGHT_SQUARE_BRACKET)
                return Indent.getNoneIndent()
            val rBracket = node.findChildByType(SppTypes.TOKEN_RIGHT_SQUARE_BRACKET)
            if (child.startOffset > lBracket.startOffset &&
                (rBracket == null || child.startOffset < rBracket.startOffset))
                return Indent.getNormalIndent()
        }

        return Indent.getNoneIndent()
    }

    override fun getChildIndent(): Indent {
        // "No ident" then indent by 1 indent.
        return Indent.getNormalIndent()
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (node.elementType in BRACE_BLOCK_TYPES) {
            val t1 = (child1 as? SppBlock)?.node?.elementType
            val t2 = (child2 as? SppBlock)?.node?.elementType
            // Empty block: collapse to "{ }" with a single space and no line breaks.
            if (t1 == SppTypes.TOKEN_LEFT_CURLY_BRACE && t2 == SppTypes.TOKEN_RIGHT_CURLY_BRACE)
                return Spacing.createSpacing(1, 1, 0, false, 0)
        }
        return Spacing.createSpacing(0, Int.MAX_VALUE, 0, true, 1)
    }

    override fun isLeaf(): Boolean = node.firstChildNode == null
}