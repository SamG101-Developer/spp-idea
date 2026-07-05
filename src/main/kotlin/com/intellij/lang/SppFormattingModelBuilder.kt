package com.intellij.lang

import com.intellij.formatting.*
import com.intellij.lang.psi.SppTypes
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock

private val BINARY_OP_TYPES = setOf(
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_0,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_1,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_2,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_3,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_4,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_5,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_6,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_7,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_8,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_9,
    SppTypes.BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_10,
    // Type-level binary ops: `or` (variant type) and `and` (intersection type)
    SppTypes.TYPE_BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_0,
    SppTypes.TYPE_BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_1,
)

private val BINARY_EXPR_TYPES = setOf(
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_0,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_1,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_2,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_3,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_4,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_5,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_6,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_7,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_8,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_9,
    SppTypes.BINARY_EXPRESSION_PRECEDENCE_LEVEL_10,
    // Type-level binary expressions
    SppTypes.TYPE_BINARY_EXPRESSION_PRECEDENCE_LEVEL_0,
    SppTypes.TYPE_BINARY_EXPRESSION_PRECEDENCE_LEVEL_1,
)

private val CHAIN_TYPES = mapOf(
    SppTypes.POSTFIX_EXPRESSION to SppTypes.POSTFIX_EXPRESSION_OP,
    SppTypes.ASSIGNMENT_TARGET_POSTFIX_EXPRESSION to SppTypes.ASSIGNMENT_TARGET_POSTFIX_EXPRESSION_OP,
)

private val MODULE_MAJOR_MEMBER_TYPES = setOf(
    SppTypes.FUNCTION_PROTOTYPE,
    SppTypes.CLASS_PROTOTYPE,
    SppTypes.SUP_PROTOTYPE_EXTENSION,
    SppTypes.SUP_PROTOTYPE_FUNCTIONS,
)

private val MODULE_MINOR_USE_TYPES = setOf(
    SppTypes.GLOBAL_USE_STATEMENT,
    SppTypes.GLOBAL_USE_VAR_STATEMENT,
)

private val MODULE_MINOR_MEMBER_TYPES = MODULE_MINOR_USE_TYPES + setOf(
    SppTypes.GLOBAL_TYPE_STATEMENT,
    SppTypes.GLOBAL_CMP_STATEMENT,
)

private val ANNOTATION_NEWLINE_TYPES = setOf(
    SppTypes.SUBROUTINE_PROTOTYPE,
    SppTypes.COROUTINE_PROTOTYPE,
    SppTypes.FUNCTION_PROTOTYPE,
    SppTypes.CLASS_PROTOTYPE,
    SppTypes.SUP_PROTOTYPE_FUNCTIONS,
    SppTypes.SUP_PROTOTYPE_EXTENSION,
    SppTypes.MODULE_PROTOTYPE,
)

// = with no surrounding spaces: keyword args, object field bindings, destructure bindings
private val ZERO_ASSIGN_SPACING_TYPES = setOf(
    SppTypes.OBJECT_INITIALIZER_ARGUMENT_KEYWORD,
    SppTypes.CASE_EXPRESSION_PATTERN_VARIANT_DESTRUCTURE_ATTRIBUTE_BINDING,
    SppTypes.LOCAL_VARIABLE_DESTRUCTURE_ATTRIBUTE_BINDING,
    SppTypes.GENERIC_ARGUMENT_COMP_KEYWORD,
    SppTypes.GENERIC_ARGUMENT_TYPE_KEYWORD,
    SppTypes.FUNCTION_CALL_ARGUMENT_KEYWORD,
)

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
                (rParen == null || child.startOffset < rParen.startOffset)
            )
                return Indent.getNormalIndent()
        }

        val lBracket = node.findChildByType(SppTypes.TOKEN_LEFT_SQUARE_BRACKET)
        if (lBracket != null) {
            val childType = child.elementType
            if (childType == SppTypes.TOKEN_LEFT_SQUARE_BRACKET || childType == SppTypes.TOKEN_RIGHT_SQUARE_BRACKET)
                return Indent.getNoneIndent()
            val rBracket = node.findChildByType(SppTypes.TOKEN_RIGHT_SQUARE_BRACKET)
            if (child.startOffset > lBracket.startOffset &&
                (rBracket == null || child.startOffset < rBracket.startOffset)
            )
                return Indent.getNormalIndent()
        }

        // Binary operators
        if (node.elementType in BINARY_EXPR_TYPES && child.elementType in BINARY_OP_TYPES)
            return Indent.getNormalIndent()

        // Chaining
        val chainOpType = CHAIN_TYPES[node.elementType]
        if (chainOpType != null && child.elementType == chainOpType)
            return Indent.getNormalIndent()

        return Indent.getNoneIndent()
    }

    override fun getChildIndent(): Indent {
        if (node.elementType in BRACE_BLOCK_TYPES) return Indent.getNormalIndent()
        if (node.findChildByType(SppTypes.TOKEN_LEFT_PARENTHESIS) != null) return Indent.getNormalIndent()
        if (node.findChildByType(SppTypes.TOKEN_LEFT_SQUARE_BRACKET) != null) return Indent.getNormalIndent()
        return Indent.getNoneIndent()
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val t1 = (child1 as? SppBlock)?.node?.elementType
        val t2 = (child2 as? SppBlock)?.node?.elementType

        // Module-level spacing rules
        if (node.elementType == SppTypes.MODULE_IMPLEMENTATION && t2 == SppTypes.MODULE_MEMBER) {
            val inner1 = if (t1 == SppTypes.MODULE_MEMBER) (child1 as SppBlock).node.firstChildNode?.elementType else null
            val inner2 = (child2 as SppBlock).node.firstChildNode?.elementType

            if (inner2 != null && inner2 in MODULE_MAJOR_MEMBER_TYPES) {
                // fun/sup/cls: exactly 1 blank line before (not before the first member)
                if (child1 != null) return Spacing.createSpacing(0, 0, 2, false, 0)
            } else if (inner2 != null && inner2 in MODULE_MINOR_MEMBER_TYPES) {
                val sameCategory = inner1 != null && inner1 in MODULE_MINOR_MEMBER_TYPES &&
                    ((inner1 in MODULE_MINOR_USE_TYPES && inner2 in MODULE_MINOR_USE_TYPES) || inner1 == inner2)
                if (sameCategory) {
                    // Same category (use/use, cmp/cmp, type/type): 0 or 1 blank lines
                    return Spacing.createSpacing(0, Int.MAX_VALUE, 1, true, 1)
                } else if (child1 != null) {
                    // Category change or preceded by major member: exactly 1 blank line
                    return Spacing.createSpacing(0, 0, 2, false, 0)
                }
            }
        }

        // Sup-level: fun must have exactly 1 blank line before (not after {)
        if (node.elementType == SppTypes.SUP_IMPLEMENTATION &&
            t1 == SppTypes.SUP_MEMBER && t2 == SppTypes.SUP_MEMBER) {
            val inner2 = (child2 as SppBlock).node.firstChildNode?.elementType
            if (inner2 == SppTypes.FUNCTION_PROTOTYPE) {
                return Spacing.createSpacing(0, 0, 2, false, 0)
            }
        }

        if (node.elementType in BRACE_BLOCK_TYPES) {
            // Empty block: collapse to "{ }" with a single space and no line breaks.
            if (t1 == SppTypes.TOKEN_LEFT_CURLY_BRACE && t2 == SppTypes.TOKEN_RIGHT_CURLY_BRACE)
                return Spacing.createSpacing(1, 1, 0, false, 0)
        }

        // Binary operators: 1 space on either side
        if (node.elementType in BINARY_EXPR_TYPES && (t1 in BINARY_OP_TYPES || t2 in BINARY_OP_TYPES))
            return Spacing.createSpacing(1, 1, 0, true, 1)

        // = sign: no spaces for keyword-arg / binding / destructure contexts; 1 space elsewhere
        if (t1 == SppTypes.TOKEN_ASSIGN || t2 == SppTypes.TOKEN_ASSIGN) {
            return if (node.elementType in ZERO_ASSIGN_SPACING_TYPES)
                Spacing.createSpacing(0, 0, 0, false, 0)
            else
                Spacing.createSpacing(1, 1, 0, true, 1)
        }

        // Top-level declarations (fun/cls/sup) require a newline after the last annotation.
        // Inline annotations like `!public start: S32` on class fields stay on one line.
        if (t1 == SppTypes.ANNOTATION && t2 != SppTypes.ANNOTATION &&
            node.elementType in ANNOTATION_NEWLINE_TYPES)
            return Spacing.createSpacing(0, Int.MAX_VALUE, 1, true, 1)

        return Spacing.createSpacing(0, Int.MAX_VALUE, 0, true, 1)
    }

    override fun isLeaf(): Boolean = node.firstChildNode == null
}