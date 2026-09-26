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

// The implementation to provide the "sticky lines" feature,
// where the enclosing trop-level block is shown at the top
// of the editor, after scrolling past it but whilst still
// in the body.
class SppBreadcrumbsProvider : BreadcrumbsProvider {

  // The language that this provider is for. This is used to
  // determine which files this provider is applicable to.
  override fun getLanguages(): Array<Language> = arrayOf(SppLanguage.INSTANCE)

  // The list of elements to accept as being "sticky". This
  // is primary top level asts like functions / classes, but
  // might extend to more asts, like "case" and "loop" blocks.
  override fun acceptElement(element: PsiElement): Boolean = when (element) {
    is SppSubroutinePrototype,
    is SppCoroutinePrototype,
    is SppClassPrototype,
    is SppSupPrototypeFunctions,
    is SppSupPrototypeExtension,
    is SppCaseOfExpression -> true

    else -> false
  }

  // The element's that are bodies of sticky elements (above),
  // used to determine where the token starts for the body of
  // the enclosing ast.
  override fun acceptStickyElement(element: PsiElement): Boolean = when (element) {
    is SppFunctionImplementation,
    is SppClassImplementation,
    is SppSupImplementation,
    is SppCaseOfExpression -> true

    else -> false
  }

  // How to format the sticky line; a simplification of the
  // element's text, to avoid showing unrequired information.
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
