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


// The `SppAnnotator` class provides custom highlighting based
// on the semantic positioning of certain ASTs. For example, we
// want `IdentifierAsts` to highlight differently if they are a
// method being called, etc.
class SppAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    // Match element type against SppConvention, SppIdentifier
    // etc.
    val parent = element.parent
    when (element) {
      // Treat a subroutine ("fun") identifier as a "field" for
      // highlighting.
      is SppIdentifier if parent is SppSubroutinePrototype -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
          .create()
      }

      // Treat a coroutine ("cor") identifier as a "field" for
      // highlighting.
      is SppIdentifier if parent is SppCoroutinePrototype -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
          .create()
      }

      // Treat a class field as a "field" for highlighting.
      is SppIdentifier if parent is SppClassAttribute -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
          .create()
      }

      // Treat a runtime member access as a "field" for highlighting.
      is SppIdentifier if parent is SppPostfixExpressionOpRuntimeMemberAccess -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
          .create()
      }

      // Root of a use-var chain is always a namespace root, so
      // highlight with the type highlighting.
      is SppIdentifier if parent is SppParsePostfixExpressionStrictlyStaticAccessOne -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER)
          .create()
      }

      is SppIdentifier if parent is SppPostfixExpressionOpStaticMemberAccess -> {
        val attr: TextAttributesKey = when (// For a use-var chain, the last identifier is the
          // referenced var/func; others are namespace segments.
          val grandParent = parent.parent) {
          is SppParsePostfixExpressionStrictlyStaticAccessOne -> {
            val isLast = grandParent.postfixExpressionOpStaticMemberAccessList.lastOrNull() == parent
            if (isLast) SppSyntaxHighlighter.IDENTIFIER
            else SppSyntaxHighlighter.TYPE_IDENTIFIER
          }

          // Annotation path: SppAnnotation already colours the
          // full range, so don't override.
          is SppParsePostfixExpressionStrictlyStaticAccessZero -> return

          // Normal postfix expression: intermediate namespace:
          // TYPE_IDENTIFIER, function call target: FUNCTION_CALL,
          // last with no call: IDENTIFIER.
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

        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(attr)
          .create()
      }

      is SppIdentifier if parent is SppPrimaryExpression -> {
        // Primary expression base: namespace root in a function call
        // for example -> TYPE_IDENTIFIER; or direct function call ->
        // FUNCTION_CALL.
        val ops = (parent.parent as? SppPostfixExpression)?.postfixExpressionOpList ?: return
        when {
          ops.any { it.postfixExpressionOpStaticMemberAccess != null } -> holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER)
            .create()

          ops.firstOrNull()?.postfixExpressionOpFunctionCall != null -> holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(SppSyntaxHighlighter.FUNCTION_CALL)
            .create()
        }
      }

      // Treat an object initialiser argument as a "field" for
      // highlighting.
      is SppIdentifier if parent is SppObjectInitializerArgumentKeyword && element == parent.getIdentifier() -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
          .create()
      }

      // Treat an object destructure identifier as a "field" for
      // highlighting.
      is SppIdentifier if parent is SppCaseExpressionPatternVariantSingleIdentifier -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.ATTRIBUTE)
          .create()
      }

      // Treat a namespace as a "type" for highlighting.
      is SppIdentifier if parent is SppTypeUnaryExpressionOperatorNamespace -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.TYPE_IDENTIFIER)
          .create()
      }

      // Special highlighting for string literals.
      is SppLiteralString -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.STRING)
          .create()
        annotateEscapeSequences(element, holder)
      }

      // Special highlighting for char literals (use string
      // highlighting).
      is SppLiteralChar -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.STRING)
          .create()
        annotateEscapeSequences(element, holder)
      }

      // Special highlighting for annotations.
      is SppAnnotation -> {
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(element)
          .textAttributes(SppSyntaxHighlighter.ANNOTATION)
          .create()
      }

      // Highlight comments a different colour when in the docstring
      // position
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

  // Regex to detect docstring tags and their values. Matches
  // @tag and optional value after whitespace, but not a colon
  // (which is used for type annotations).
  private val docstringTagPattern = Regex("""(@\w+)(?:\s+([^\s:]+))?""")

  // Regex to detect inline code in docstrings, e.g. `code`. Not
  // for multi-line code blocks. These are not yet supported for
  // the docstrings. Todo: Enable in the future.
  private val inlineCodePattern = Regex("""`[^`\n]+`""")

  // Regex to detect recognised escape sequences in string and
  // char literals. Matches \n, \t, \r, \0, \\, \', and \".
  private val escapeSequencePattern = Regex("""\\[ntr0\\'"]""")

  // Highlights the recognised escape sequences inside a string
  // or char literal, layered on top of the literal's base STRING
  // colour (set by the caller just before this).
  private fun annotateEscapeSequences(element: PsiElement, holder: AnnotationHolder) {
    val text = element.text
    if (text.length < 2) return
    val base = element.textRange.startOffset

    // The lexer's literal regex never allows the delimiter quote
    // to appear mid-body, so the last character is always the
    // closing quote itself, never part of an escape. Exclude it
    // from the search for escape sequences.
    val body = text.substring(0, text.length - 1)
    for (match in escapeSequencePattern.findAll(body)) {
      holder
        .newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(TextRange(base + match.range.first, base + match.range.last + 1))
        .textAttributes(SppSyntaxHighlighter.VALID_ESCAPE)
        .create()
    }
  }

  // Provide special annotation for docstring tags, e.g. @let,
  // and their values, e.g. `foo` in `@param foo`. This is
  // just to prettify the docstring.
  private fun annotateDocstringTags(comment: PsiElement, holder: AnnotationHolder) {
    val text = comment.text
    val base = comment.textRange.startOffset

    // @tag and value highlighting
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

    // Inline code: `...`
    for (match in inlineCodePattern.findAll(text)) {
      holder
        .newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(TextRange(base + match.range.first, base + match.range.last + 1))
        .textAttributes(SppSyntaxHighlighter.DOCSTRING_INLINE_CODE)
        .create()
    }
  }

  // Walk backwards through previous siblings. A comment is
  // part of the docstring if everything between it and the
  // opening '{' is other comments or single-line whitespace;
  // a blank line (\n\n) breaks the docstring block.
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
