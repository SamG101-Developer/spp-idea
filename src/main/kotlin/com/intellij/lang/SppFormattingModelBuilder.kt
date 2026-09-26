package com.intellij.lang

import com.intellij.formatting.*
import com.intellij.lang.psi.SppTypes
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock

// Collection of all the binary operator types, for spacing
// and indentation rules. The binary operators are split
// into precedence levels. Both expression-level and type-
// level binary operators are included.
private val BINARY_OP_TYPES = setOf(
  // Expression-level binary ops.
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
  // Type-level binary ops.
  SppTypes.TYPE_BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_0,
  SppTypes.TYPE_BINARY_EXPRESSION_OP_PRECEDENCE_LEVEL_1,
)

// Collection of all the binary expression types, for spacing
// and indentation rules.
private val BINARY_EXPR_TYPES = setOf(
  // Expression-level binary expressions.
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
  // Type-level binary expressions.
  SppTypes.TYPE_BINARY_EXPRESSION_PRECEDENCE_LEVEL_0,
  SppTypes.TYPE_BINARY_EXPRESSION_PRECEDENCE_LEVEL_1,
)

// Collection of all the chainable expression types, which
// include the areas "." and "::" runtime/static access postfix
// operators.
private val CHAIN_TYPES = mapOf(
  SppTypes.POSTFIX_EXPRESSION to SppTypes.POSTFIX_EXPRESSION_OP,
  SppTypes.ASSIGNMENT_TARGET_POSTFIX_EXPRESSION to SppTypes.ASSIGNMENT_TARGET_POSTFIX_EXPRESSION_OP,
)

// Top level module members are split into "major" and "minor"
// categories for spacing rules. Major members (fun/cls/sup)
// require a blank line before them.
private val MODULE_MAJOR_MEMBER_TYPES = setOf(
  SppTypes.FUNCTION_PROTOTYPE,
  SppTypes.CLASS_PROTOTYPE,
  SppTypes.SUP_PROTOTYPE_EXTENSION,
  SppTypes.SUP_PROTOTYPE_FUNCTIONS,
)

// Minor members (use/type/cmp) can be grouped together with 0
// or 1 blank lines between them.
private val MODULE_MINOR_USE_TYPES = setOf(
  SppTypes.GLOBAL_USE_STATEMENT,
  SppTypes.GLOBAL_USE_VAR_STATEMENT,
)

// Minor members (use/type/cmp) can be grouped together with 0
// or 1 blank lines between them, but 1 blank line is required
// between different categories (use vs cmp vs type).
private val MODULE_MINOR_MEMBER_TYPES = MODULE_MINOR_USE_TYPES + setOf(
  SppTypes.GLOBAL_TYPE_STATEMENT,
  SppTypes.GLOBAL_CMP_STATEMENT,
)

// No spacing is required between annotations (can be same line),
// but the last annotation must have a newline before the element
// that they are annotating.
private val ANNOTATION_NEWLINE_TYPES = setOf(
  SppTypes.SUBROUTINE_PROTOTYPE,
  SppTypes.COROUTINE_PROTOTYPE,
  SppTypes.FUNCTION_PROTOTYPE,
  SppTypes.CLASS_PROTOTYPE,
  SppTypes.SUP_PROTOTYPE_FUNCTIONS,
  SppTypes.SUP_PROTOTYPE_EXTENSION,
  SppTypes.MODULE_PROTOTYPE,
)

// Places where he use "=" but want no spacing for, like object
// initialiser arguments, destructure bindings, generic argument
// keywords and function argument keywords.
private val ZERO_ASSIGN_SPACING_TYPES = setOf(
  SppTypes.OBJECT_INITIALIZER_ARGUMENT_KEYWORD,
  SppTypes.CASE_EXPRESSION_PATTERN_VARIANT_DESTRUCTURE_ATTRIBUTE_BINDING,
  SppTypes.LOCAL_VARIABLE_DESTRUCTURE_ATTRIBUTE_BINDING,
  SppTypes.GENERIC_ARGUMENT_COMP_KEYWORD,
  SppTypes.GENERIC_ARGUMENT_TYPE_KEYWORD,
  SppTypes.FUNCTION_CALL_ARGUMENT_KEYWORD,
  SppTypes.GENERIC_PARAMETER_TYPE_OPTIONAL,
)

// Auto indentation rules for the body of a block, which
// is any construct that has a "{" and "}" pair. The body
// is indented one level deeper than the opening brace,
// and the closing brace is not indented. The exception is
// the "case_of_expression", which has a header before the
// opening brace, and only the branches are indented.
private val BRACE_BLOCK_TYPES = setOf(
  SppTypes.FUNCTION_IMPLEMENTATION,
  SppTypes.CLASS_IMPLEMENTATION,
  SppTypes.SUP_IMPLEMENTATION,
  SppTypes.INNER_SCOPE_EXPRESSION,
  SppTypes.CASE_OF_EXPRESSION,
)

// The formatting model builder is the entry point for the
// formatter, and is called by the IDE when formatting is
// requested.
class SppFormattingModelBuilder : FormattingModelBuilder {
  override fun createModel(context: FormattingContext): FormattingModel {
    val root = SppBlock(
      context.node, Indent.getNoneIndent(), context.codeStyleSettings
    )
    return FormattingModelProvider.createFormattingModelForPsiFile(
      context.containingFile, root, context.codeStyleSettings
    )
  }
}

// The SppBlock class is the core of the formatting model, and
// is responsible for determining the indentation and spacing
// of each node in the AST. It is a recursive structure, where
// each block can have child blocks, and the indentation and
// spacing is determined by the parent block.
class SppBlock(
  node: ASTNode,
  private val indent: Indent,
  private val settings: CodeStyleSettings,
) : AbstractBlock(node, null, null) {

  // Return the class-level indent that's stored and manipulated.
  override fun getIndent(): Indent = indent

  // Return the list of child blocks, which are the non-whitespace
  // children of the AST node. Each child block is created
  // with the appropriate indentation, which is determined by
  // the childIndentFor function.
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

  // Determine the indentation for a child node, based on the
  // braces and parentheses that are present in the parent node.
  private fun childIndentFor(child: ASTNode): Indent {
    // For elements that are enclosed in braces, indent the
    // children one level deeper than the opening brace, except
    // for the opening and closing braces themselves.
    if (node.elementType in BRACE_BLOCK_TYPES) {
      val childType = child.elementType
      if (childType == SppTypes.TOKEN_LEFT_CURLY_BRACE || childType == SppTypes.TOKEN_RIGHT_CURLY_BRACE) return Indent.getNoneIndent()

      // case_of_expression has a header (case expr of) before
      // its {; only indent the branches
      if (node.elementType == SppTypes.CASE_OF_EXPRESSION) {
        val lBrace = node.findChildByType(SppTypes.TOKEN_LEFT_CURLY_BRACE) ?: return Indent.getNoneIndent()
        return if (child.startOffset > lBrace.startOffset) Indent.getNormalIndent() else Indent.getNoneIndent()
      }

      return Indent.getNormalIndent()
    }

    // For elements that are enclosed in parentheses, indent the
    // children one level deeper than the opening parenthesis,
    // except for the opening and closing parentheses themselves.
    // Multiline arguments get the "continuation indent".
    val lParen = node.findChildByType(SppTypes.TOKEN_LEFT_PARENTHESIS)
    if (lParen != null) {
      val childType = child.elementType
      if (childType == SppTypes.TOKEN_LEFT_PARENTHESIS || childType == SppTypes.TOKEN_RIGHT_PARENTHESIS) return Indent.getNoneIndent()
      val rParen = node.findChildByType(SppTypes.TOKEN_RIGHT_PARENTHESIS)
      if (child.startOffset > lParen.startOffset && (rParen == null || child.startOffset < rParen.startOffset)) return Indent.getNormalIndent()
    }

    // For elements that are enclosed in square brackets, indent
    // the children one level deeper than the opening bracket,
    // except for the opening and closing brackets themselves.
    // For example with index/slice/generic operators.
    val lBracket = node.findChildByType(SppTypes.TOKEN_LEFT_SQUARE_BRACKET)
    if (lBracket != null) {
      val childType = child.elementType
      if (childType == SppTypes.TOKEN_LEFT_SQUARE_BRACKET || childType == SppTypes.TOKEN_RIGHT_SQUARE_BRACKET) return Indent.getNoneIndent()
      val rBracket = node.findChildByType(SppTypes.TOKEN_RIGHT_SQUARE_BRACKET)
      if (child.startOffset > lBracket.startOffset && (rBracket == null || child.startOffset < rBracket.startOffset)) return Indent.getNormalIndent()
    }

    // Binary operators on a newline get the normal indent,
    // allowing a single indent aligned "or", "+" etc.
    if (node.elementType in BINARY_EXPR_TYPES && child.elementType in BINARY_OP_TYPES)
      return Indent.getNormalIndent()

    // Chaining onto a newline does the same as binary operators,
    // applying a single indent to align the "." or "::" operator.
    val chainOpType = CHAIN_TYPES[node.elementType]
    if (chainOpType != null && child.elementType == chainOpType) return Indent.getNormalIndent()

    // Otherwise, no indentation is applied to the child node.
    return Indent.getNoneIndent()
  }

  // The difference between this and the "childIndentFor" function
  // is that this is for the children of the current node, and the
  // other is for the children of the parent node. This is used to
  // determine if the current node should be indented or not.
  override fun getChildIndent(): Indent {
    if (node.elementType in BRACE_BLOCK_TYPES) return Indent.getNormalIndent()
    if (node.findChildByType(SppTypes.TOKEN_LEFT_PARENTHESIS) != null) return Indent.getNormalIndent()
    if (node.findChildByType(SppTypes.TOKEN_LEFT_SQUARE_BRACKET) != null) return Indent.getNormalIndent()
    return Indent.getNoneIndent()
  }

  // The function that determines the spacing between two child
  // blocks, based on the types of the child blocks and the
  // parent block.
  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    val t1 = (child1 as? SppBlock)?.node?.elementType
    val t2 = (child2 as? SppBlock)?.node?.elementType

    // Module-level spacing rules.
    if (node.elementType == SppTypes.MODULE_IMPLEMENTATION && t2 == SppTypes.MODULE_MEMBER) {
      // Walk backwards past any trailing comments to find the
      // real previous MODULE_MEMBER.
      val prevMemberNode: ASTNode? = when (t1) {
        SppTypes.MODULE_MEMBER -> (child1 as SppBlock).node
        null -> null
        else -> {
          var sib = (child1 as SppBlock).node.treePrev
          while (sib != null && sib.elementType != SppTypes.MODULE_MEMBER) sib = sib.treePrev
          sib
        }
      }

      // The true 2 nodes being checked, irrespective of the
      // comments in between. These element types are used for
      // the spacing rules.
      val inner1 = prevMemberNode?.firstChildNode?.elementType
      val inner2 = (child2 as SppBlock).node.firstChildNode?.elementType

      // fun/sup/cls: exactly 1 blank line before the declaration
      // (not before the first member).
      if (inner2 != null && inner2 in MODULE_MAJOR_MEMBER_TYPES) {
        if (inner1 != null) return Spacing.createSpacing(0, 0, 2, false, 0)

        // use/type/cmp: 0 or 1 blank lines between same category,
        // 1 blank line between different categories.
      } else if (inner2 != null && inner2 in MODULE_MINOR_MEMBER_TYPES) {
        val sameCategory =
          inner1 != null && inner1 in MODULE_MINOR_MEMBER_TYPES && ((inner1 in MODULE_MINOR_USE_TYPES && inner2 in MODULE_MINOR_USE_TYPES) || inner1 == inner2)
        if (sameCategory) {
          // Same category (use/use, cmp/cmp, type/type): 0 or 1
          // blank lines.
          return Spacing.createSpacing(0, Int.MAX_VALUE, 1, true, 1)
        } else if (inner1 != null) {
          // Category change or preceded by major member: exactly
          // 1 blank line.
          return Spacing.createSpacing(0, 0, 2, false, 0)
        }
      }
    }

    // Sup-level: fun must have exactly 1 blank line before (not
    // after {)
    if (node.elementType == SppTypes.SUP_IMPLEMENTATION && t1 == SppTypes.SUP_MEMBER && t2 == SppTypes.SUP_MEMBER) {
      val inner2 = (child2 as SppBlock).node.firstChildNode?.elementType
      if (inner2 == SppTypes.FUNCTION_PROTOTYPE) {
        return Spacing.createSpacing(0, 0, 2, false, 0)
      }
    }

    if (node.elementType in BRACE_BLOCK_TYPES) {
      // Empty block: collapse to "{ }" with a single space and
      // no line breaks.
      if (t1 == SppTypes.TOKEN_LEFT_CURLY_BRACE && t2 == SppTypes.TOKEN_RIGHT_CURLY_BRACE) return Spacing.createSpacing(
        1, 1, 0, false, 0
      )

      // "{" followed by content, or content followed by "}", on
      // the same line: exactly 1 space. A line break here (e.g.
      // the normal multi-line block layout) is preserved as-is.
      if (t1 == SppTypes.TOKEN_LEFT_CURLY_BRACE || t2 == SppTypes.TOKEN_RIGHT_CURLY_BRACE) return Spacing.createSpacing(
        1, 1, 0, true, 1
      )
    }

    // Binary operators: 1 space on either side
    if (node.elementType in BINARY_EXPR_TYPES && (t1 in BINARY_OP_TYPES || t2 in BINARY_OP_TYPES)) return Spacing.createSpacing(
      1, 1, 0, true, 1
    )

    // = sign: no spaces for keyword-arg / binding / destructure
    // contexts; 1 space elsewhere
    if (t1 == SppTypes.TOKEN_ASSIGN || t2 == SppTypes.TOKEN_ASSIGN) {
      return if (node.elementType in ZERO_ASSIGN_SPACING_TYPES) Spacing.createSpacing(0, 0, 0, false, 0)
      else Spacing.createSpacing(1, 1, 0, true, 1)
    }

    // Top-level declarations (fun/cls/sup) require a newline
    // after the last annotation. Inline annotations like
    // `!public start: S32` on class fields stay on one line.
    if (t1 == SppTypes.ANNOTATION && t2 != SppTypes.ANNOTATION && node.elementType in ANNOTATION_NEWLINE_TYPES) return Spacing.createSpacing(
      0, Int.MAX_VALUE, 1, true, 1
    )

    // Default spacing: 0 or 1 blank lines between any other
    // elements, with a line break preserved if present.
    return Spacing.createSpacing(0, Int.MAX_VALUE, 0, true, 1)
  }

  // Return true if the current node is a leaf node (has no
  // children). This is used to determine if the block should
  // be collapsed or not. Leaf nodes are not collapsed, and
  // are always shown in full.
  override fun isLeaf(): Boolean = node.firstChildNode == null
}