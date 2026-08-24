package com.intellij.lang

import com.intellij.lang.psi.SppCaseOfExpression
import com.intellij.lang.psi.SppClassImplementation
import com.intellij.lang.psi.SppClassPrototype
import com.intellij.lang.psi.SppCoroutinePrototype
import com.intellij.lang.psi.SppFunctionImplementation
import com.intellij.lang.psi.SppSubroutinePrototype
import com.intellij.lang.psi.SppSupImplementation
import com.intellij.lang.psi.SppSupPrototypeExtension
import com.intellij.lang.psi.SppSupPrototypeFunctions
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

/**
 * There is no plugin-facing "sticky lines" extension point — sticky lines (like the bottom
 * breadcrumbs bar) are built by the platform directly on top of [BreadcrumbsProvider] (see
 * `StickyLinesLanguageSupport`/`StickyLinesCollector` in
 * `com.intellij.openapi.editor.impl.stickyLines`, which are internal and not extensible).
 * Implementing this is therefore what actually turns sticky lines on for S++.
 */
class SppBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> = arrayOf(SppLanguage.INSTANCE)

    override fun acceptElement(element: PsiElement): Boolean = when (element) {
        is SppSubroutinePrototype,
        is SppCoroutinePrototype,
        is SppClassPrototype,
        is SppSupPrototypeFunctions,
        is SppSupPrototypeExtension,
        is SppCaseOfExpression -> true
        else -> false
    }

    /**
     * The platform pins whichever *document line* [PsiElement.getTextOffset] falls on — it renders
     * that line's real source text verbatim, it doesn't call [getElementInfo]. `subroutine_prototype`,
     * `coroutine_prototype` and `class_prototype` all start with `(annotation)*`, so their own
     * `getTextOffset()` (unoverridden, defaults to their text-range start) lands on the *annotation's*
     * line whenever one is present — e.g. `@foo` pins instead of `fun bar() -> Int {`. Their
     * `*_implementation` child starts at the `{` on the header's own line and spans the same body, so
     * sticking to that instead sidesteps the annotation without needing a PSI mixin.
     */
    override fun acceptStickyElement(element: PsiElement): Boolean = when (element) {
        is SppFunctionImplementation,
        is SppClassImplementation,
        is SppSupImplementation,
        is SppCaseOfExpression -> true
        else -> false
    }

    override fun getElementInfo(element: PsiElement): String = when (element) {
        is SppSubroutinePrototype -> "fun ${element.identifier.text}"
        is SppCoroutinePrototype -> "cor ${element.identifier.text}"
        is SppClassPrototype -> "cls ${element.upperIdentifier.text}"
        is SppSupPrototypeFunctions -> "sup ${element.typeExpression.text}"
        is SppSupPrototypeExtension -> "sup " + element.typeExpressionList.joinToString(" ext ") { it.text }
        is SppCaseOfExpression -> "case ${element.expression.text} of"
        else -> element.text
    }
}