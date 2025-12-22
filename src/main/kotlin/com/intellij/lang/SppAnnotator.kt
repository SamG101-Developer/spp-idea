package com.intellij.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.psi.*
import com.intellij.lang.psi.impl.*
import com.intellij.psi.PsiElement

class SppAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Match element type against SppConvention, SppIdentifier etc
        when (element) {
            is SppIdentifier if element.parent is SppSubroutinePrototypeImpl -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }
            is SppIdentifier if element.parent is SppCoroutinePrototypeImpl -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }
            is SppIdentifier if element.parent is SppClassAttributeImpl -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }
            is SppIdentifier if element.parent is SppPostfixExpressionOpRuntimeMemberAccessImpl -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(SppSyntaxHighlighter.ATTRIBUTE).create()
            }
            is SppIdentifier if element.parent is SppPostfixExpressionOpStaticMemberAccessImpl -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER).create()
            }
            is SppAnnotation -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(SppSyntaxHighlighter.ANNOTATION).create()
            }
        }
    }
}