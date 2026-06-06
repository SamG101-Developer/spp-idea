package com.intellij.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.psi.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

class SppAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Match element type against SppConvention, SppIdentifier etc
        val parent = element.parent
        when (element) {
            is SppIdentifier if parent is SppSubroutinePrototype -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
                    .create()
            }

            is SppIdentifier if parent is SppCoroutinePrototype -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
                    .create()
            }

            is SppIdentifier if parent is SppClassAttribute -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
                    .create()
            }

            is SppIdentifier if parent is SppPostfixExpressionOpRuntimeMemberAccess -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
                    .create()
            }

            is SppIdentifier if parent is SppPostfixExpressionOpStaticMemberAccess -> {
                // If the immediately following op in the postfix chain is a function call,
                // this is the call target (align_of in std::mem::align_of()) -> FUNCTION_CALL.
                // Otherwise, it's a namespace segment -> TYPE_IDENTIFIER.
                val op = parent.parent as? SppPostfixExpressionOp
                val ops = (op?.parent as? SppPostfixExpression)?.postfixExpressionOpList ?: emptyList()
                val nextOp = op?.let { ops.getOrNull(ops.indexOf(it) + 1) }
                val attr = if (nextOp?.postfixExpressionOpFunctionCall != null)
                    SppSyntaxHighlighter.FUNCTION_CALL
                else
                    SppSyntaxHighlighter.TYPE_IDENTIFIER
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(attr)
                    .create()
            }

            is SppIdentifier if parent is SppPrimaryExpression -> {
                // Primary expression base: namespace root (std in std::mem::foo()) -> TYPE_IDENTIFIER,
                // or direct function call (a in a()) -> FUNCTION_CALL.
                val ops = (parent.parent as? SppPostfixExpression)?.postfixExpressionOpList ?: return
                when {
                    ops.any { it.postfixExpressionOpStaticMemberAccess != null } ->
                        holder
                            .newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(element)
                            .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER)
                            .create()

                    ops.any { it.postfixExpressionOpFunctionCall != null } ->
                        holder
                            .newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(element)
                            .textAttributes(SppSyntaxHighlighter.FUNCTION_CALL)
                            .create()
                }
            }

            is SppIdentifier if parent is SppObjectInitializerArgumentKeyword && element == parent.getIdentifier() -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
                    .create()
            }

            is SppIdentifier if parent is SppCaseExpressionPatternVariantSingleIdentifier -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
                    .create()
            }

            is SppIdentifier if parent is SppTypeUnaryExpressionOperatorNamespace -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER)
                    .create()
            }

            is SppLiteralString -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.STRING)
                    .create()
            }

            is SppLiteralChar -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.STRING)
                    .create()
            }

            is SppAnnotation -> {
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(SppSyntaxHighlighter.ANNOTATION)
                    .create()
            }

            is PsiComment -> {
                val p = element.parent
                if ((p is SppFunctionImplementation || p is SppClassImplementation || p is SppSupImplementation)
                    && isDocstringComment(element)
                ) {
                    holder
                        .newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(element)
                        .textAttributes(SppSyntaxHighlighter.DOCSTRING)
                        .create()
                    annotateDocstringTags(element, holder)
                }
            }
        }
    }

    // Pattern: @tag optionally followed by a value token (anything up to whitespace or colon).
    private val docstringTagPattern = Regex("""(@\w+)(?:\s+([^\s:]+))?""")

    private fun annotateDocstringTags(comment: PsiElement, holder: AnnotationHolder) {
        val text = comment.text
        val base = comment.textRange.startOffset
        for (match in docstringTagPattern.findAll(text)) {
            val tagGroup = match.groups[1]!!
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + tagGroup.range.first, base + tagGroup.range.last + 1))
                .textAttributes(SppSyntaxHighlighter.DOCSTRING_TAG)
                .create()

            val valueGroup = match.groups[2] ?: continue
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + valueGroup.range.first, base + valueGroup.range.last + 1))
                .textAttributes(SppSyntaxHighlighter.DOCSTRING_TAG_VALUE)
                .create()
        }
    }

    // Walk backwards through previous siblings. A comment is part of the docstring
    // if everything between it and the opening '{' is other comments or single-line
    // whitespace — a blank line (\n\n) breaks the docstring block.
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
