package com.intellij.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.psi.*
import com.intellij.psi.PsiElement

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
        }
    }
}