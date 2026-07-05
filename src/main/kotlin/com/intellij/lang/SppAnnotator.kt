package com.intellij.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.psi.*
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

/**
 * The `SppAnnotator` class provides custom highlighting based on the semantic positioning of certain ASTs. For example,
 * we want `IdentifierAsts` to highlight differently if they are a method being called, etc.
 */
class SppAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Match element type against SppConvention, SppIdentifier etc
        val parent = element.parent
        when (element) {
            // Treat a subroutine ("fun") identifier as a "field" for highlighting.
            is SppIdentifier if parent is SppSubroutinePrototype -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            // Treat a coroutine ("cor") identifier as a "field" for highlighting.
            is SppIdentifier if parent is SppCoroutinePrototype -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            // Treat a class field as a "field" for highlighting.
            is SppIdentifier if parent is SppClassAttribute -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            // Treat a runtime member access as a "field" for highlighting.
            is SppIdentifier if parent is SppPostfixExpressionOpRuntimeMemberAccess -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            // Root of a use-var chain (e.g. `std` in `use std::ops::malloc`) is always a namespace root, so highlight
            // with the type highlighting.
            is SppIdentifier if parent is SppParsePostfixExpressionStrictlyStaticAccessOne -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER).create()
            }

            is SppIdentifier if parent is SppPostfixExpressionOpStaticMemberAccess -> {
                val grandParent = parent.parent
                val attr: TextAttributesKey = when {
                    // For a use-var chain, the last identifier is the referenced var/func; others are namespace
                    // segments.
                    grandParent is SppParsePostfixExpressionStrictlyStaticAccessOne -> {
                        val isLast = grandParent.postfixExpressionOpStaticMemberAccessList.lastOrNull() == parent
                        if (isLast) SppSyntaxHighlighter.IDENTIFIER
                        else SppSyntaxHighlighter.TYPE_IDENTIFIER
                    }

                    // Annotation path: SppAnnotation already colours the full range, so don't override.
                    grandParent is SppParsePostfixExpressionStrictlyStaticAccessZero -> return

                    // Normal postfix expression: intermediate namespace: TYPE_IDENTIFIER,
                    // function call target: FUNCTION_CALL, last with no call: IDENTIFIER.
                    else -> {
                        val op = grandParent as? SppPostfixExpressionOp
                        val ops = (op?.parent as? SppPostfixExpression)?.postfixExpressionOpList ?: emptyList()
                        val nextOp = op?.let { ops.getOrNull(ops.indexOf(it) + 1) }
                        when {
                            nextOp?.postfixExpressionOpFunctionCall != null -> SppSyntaxHighlighter.FUNCTION_CALL
                            nextOp == null -> SppSyntaxHighlighter.IDENTIFIER
                            else -> SppSyntaxHighlighter.TYPE_IDENTIFIER
                        }
                    }
                }
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(attr).create()
            }

            is SppIdentifier if parent is SppPrimaryExpression -> {
                // Primary expression base: namespace root (std in std::mem::foo()) -> TYPE_IDENTIFIER, or direct
                // function call (a in a()) -> FUNCTION_CALL.
                val ops = (parent.parent as? SppPostfixExpression)?.postfixExpressionOpList ?: return
                when {
                    ops.any { it.postfixExpressionOpStaticMemberAccess != null } -> holder.newSilentAnnotation(
                        HighlightSeverity.INFORMATION
                    ).range(element).textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER).create()

                    ops.firstOrNull()?.postfixExpressionOpFunctionCall != null -> holder.newSilentAnnotation(
                        HighlightSeverity.INFORMATION
                    ).range(element).textAttributes(SppSyntaxHighlighter.FUNCTION_CALL).create()
                }
            }

            // Treat an object initializer argument as a "field" for highlighting.
            is SppIdentifier if parent is SppObjectInitializerArgumentKeyword && element == parent.getIdentifier() -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            // Treat an object destructure identifier as a "field" for highlighting.
            is SppIdentifier if parent is SppCaseExpressionPatternVariantSingleIdentifier -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            // Treat a namespace as a "type" for highlighting.
            is SppIdentifier if parent is SppTypeUnaryExpressionOperatorNamespace -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER).create()
            }

            // Special highlighting for string literals.
            is SppLiteralString -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.STRING).create()
            }

            // Special highlighting for char literals (use string highlighting).
            is SppLiteralChar -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.STRING).create()
            }

            // Special highlighting for annotations.
            is SppAnnotation -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ANNOTATION).create()
            }

            // Highlight comments a different colour when ion the docstring position
            is PsiComment -> {
                val p = element.parent
                if ((p is SppFunctionImplementation || p is SppClassImplementation || p is SppSupImplementation) && isDocstringComment(
                        element
                    )
                ) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                        .textAttributes(SppSyntaxHighlighter.DOCSTRING).create()
                    annotateDocstringTags(element, holder)
                }
            }
        }
    }

    private val docstringTagPattern = Regex("""(@\w+)(?:\s+([^\s:]+))?""")
    private val inlineCodePattern = Regex("""`[^`\n]+`""")

    private fun annotateDocstringTags(comment: PsiElement, holder: AnnotationHolder) {
        val text = comment.text
        val base = comment.textRange.startOffset

        // @tag and value highlighting
        for (match in docstringTagPattern.findAll(text)) {
            val tagGroup = match.groups[1]!!
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + tagGroup.range.first, base + tagGroup.range.last + 1))
                .textAttributes(SppSyntaxHighlighter.DOCSTRING_TAG).create()

            val valueGroup = match.groups[2] ?: continue
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + valueGroup.range.first, base + valueGroup.range.last + 1))
                .textAttributes(SppSyntaxHighlighter.DOCSTRING_TAG_VALUE).create()
        }

        // Inline code: `...`
        for (match in inlineCodePattern.findAll(text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + match.range.first, base + match.range.last + 1))
                .textAttributes(SppSyntaxHighlighter.DOCSTRING_INLINE_CODE).create()
        }
    }

    // Walk backwards through previous siblings. A comment is part of the docstring if everything between it and the
    // opening '{' is other comments or single-line whitespace; a blank line (\n\n) breaks the docstring block.
    private fun isDocstringComment(comment: PsiElement): Boolean {
        var sibling = comment.prevSibling
        while (sibling != null) {
            when {
                sibling is PsiWhiteSpace -> if (sibling.text.contains("\n\n")) return false
                sibling is PsiComment -> {}
                sibling.node.elementType == SppTypes.TOKEN_LEFT_CURLY_BRACE -> return true
                else -> return false
            }
            sibling = sibling.prevSibling
        }
        return false
    }
}
