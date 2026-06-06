package com.intellij.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.psi.*
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

class SppAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Match element type against SppConvention, SppIdentifier etc
        val parent = element.parent
        when (element) {
            is SppIdentifier if parent is SppSubroutinePrototype -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            is SppIdentifier if parent is SppCoroutinePrototype -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            is SppIdentifier if parent is SppClassAttribute -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            is SppIdentifier if parent is SppPostfixExpressionOpRuntimeMemberAccess -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            is SppIdentifier if parent is SppPostfixExpressionOpStaticMemberAccess -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER).create()
            }

            is SppIdentifier if parent is SppObjectInitializerArgumentKeyword && element == parent.getIdentifier() -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            is SppIdentifier if parent is SppCaseExpressionPatternVariantSingleIdentifier -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }

            is SppIdentifier if parent is SppTypeUnaryExpressionOperatorNamespace -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER).create()
            }

            is SppLiteralString -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.STRING).create()
            }

            is SppLiteralChar -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.STRING).create()
            }

            is SppAnnotation -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                    .textAttributes(SppSyntaxHighlighter.ANNOTATION).create()
            }

            is PsiComment -> {
                val p = element.parent
                if ((p is SppFunctionImplementation || p is SppClassImplementation || p is SppSupImplementation)
                    && isDocstringComment(element)) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                        .textAttributes(SppSyntaxHighlighter.DOCSTRING).create()
                }
            }
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