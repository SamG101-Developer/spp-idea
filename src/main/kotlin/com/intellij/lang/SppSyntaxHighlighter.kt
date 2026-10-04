package com.intellij.lang

import com.intellij.lang.psi.SppTokenSets
import com.intellij.lang.psi.SppTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors.BAD_CHARACTER
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.intellij.ui.JBColor

// The S++ syntax highlighter class, which defines the
// syntax highlighting rules for S++ files. This class is
// responsible for mapping S++ tokens to text attributes,
// which are used to colour the text in the editor.
// Todo: To be replaced by the actual s++ compiler.
class SppSyntaxHighlighter : SyntaxHighlighterBase {
  companion object {
    val IDENTIFIER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val TYPE_IDENTIFIER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)
    val KEYWORD: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val NUMBER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val STRING: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_STRING", DefaultLanguageHighlighterColors.STRING)
    val VALID_ESCAPE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey(
        "SPP_VALID_STRING_ESCAPE",
        DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
      )
    val COMMENT: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val DOCSTRING: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_DOCSTRING", DefaultLanguageHighlighterColors.DOC_COMMENT)
    val DOCSTRING_TAG: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_DOCSTRING_TAG", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)
    val DOCSTRING_TAG_VALUE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey(
        "SPP_DOCSTRING_TAG_VALUE",
        DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE
      )
    val DOCSTRING_INLINE_CODE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey(
        "SPP_DOCSTRING_INLINE_CODE",
        DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP
      )

    // A "[name]" parameter reference. Yellow by default, from the
    // plugin's additions to the bundled colour schemes.
    val DOCSTRING_PARAM_REF: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey(
        "SPP_DOCSTRING_PARAM_REF",
        DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE
      )
    val OPERATOR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val BRACKET: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
    val ATTRIBUTE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_ATTRIBUTE", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val FUNCTION_CALL: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL)
    val ANNOTATION: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_ANNOTATION", DefaultLanguageHighlighterColors.METADATA)

    // The finer keys below each fall back to the broader key the
    // same thing was coloured with before, so the defaults look the
    // same, but any of them can be given a colour of its own on the
    // S++ colour settings page.

    // Brackets, by kind.
    val PARENTHESES: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_PARENTHESES", BRACKET)
    val BRACES: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_BRACES", BRACKET)
    val SQUARE_BRACKETS: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_SQUARE_BRACKETS", BRACKET)

    // A char literal, apart from a string.
    val CHAR: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_CHAR", STRING)

    // Namespaces.
    val NAMESPACE: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_NAMESPACE", TYPE_IDENTIFIER)

    // A "cmp" constant or comp generic parameter, where it is
    // declared and wherever it is used. Uses are only known once
    // the compiler has resolved the names, so they are coloured
    // from its analysis.
    val CMP_IDENTIFIER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_CMP_IDENTIFIER", DefaultLanguageHighlighterColors.CONSTANT)

    // Declarations.
    val FUNCTION_DECLARATION: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_FUNCTION_DECLARATION", ATTRIBUTE)
    val COROUTINE_DECLARATION: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_COROUTINE_DECLARATION", FUNCTION_DECLARATION)
    val ATTRIBUTE_DECLARATION: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_ATTRIBUTE_DECLARATION", ATTRIBUTE)

    // Uses of members and calls.
    val MEMBER_ACCESS: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_MEMBER_ACCESS", ATTRIBUTE)
    val METHOD_CALL: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_METHOD_CALL", ATTRIBUTE)
    val NAMESPACED_CALL: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_NAMESPACED_CALL", FUNCTION_CALL)
    val NAMED_ARGUMENT: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_NAMED_ARGUMENT", ATTRIBUTE)
    val PATTERN_FIELD: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPP_PATTERN_FIELD", ATTRIBUTE)

    // What the compiler's analysis adds: the body of a function an
    // error stopped in, and the text written at the end of a line
    // (an error, what explains it, a "cmp" value). Their colours
    // come from the plugin's additions to the bundled schemes.
    val UNREACHED_CODE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_UNREACHED_CODE", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
    val INLINE_ERROR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_INLINE_ERROR", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val INLINE_EXPLANATION: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_INLINE_EXPLANATION", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val INLINE_CMP_VALUE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("SPP_INLINE_CMP_VALUE", DefaultLanguageHighlighterColors.LINE_COMMENT)

    // The gutter's analysis-state dots, which are drawn, not text.
    val INDEX_PENDING: ColorKey = ColorKey.createColorKey("SPP_INDEX_PENDING", JBColor.GRAY)
    val INDEX_ANALYSING: ColorKey = ColorKey.createColorKey("SPP_INDEX_ANALYSING", JBColor(0xE8C200, 0xD9B400))
    val INDEX_INDEXED: ColorKey = ColorKey.createColorKey("SPP_INDEX_INDEXED", JBColor(0x59A869, 0x499C54))
    val INDEX_INCOMPLETE: ColorKey = ColorKey.createColorKey("SPP_INDEX_INCOMPLETE", JBColor(0xDB5860, 0xC75450))

    val BAD_CHAR_KEYS: Array<TextAttributesKey> = arrayOf(BAD_CHARACTER)
    val EMPTY_KEYS: Array<TextAttributesKey> = arrayOf()
    val IDENTIFIER_KEYS: Array<TextAttributesKey> = arrayOf(IDENTIFIER)
    val TYPE_IDENTIFIER_KEYS: Array<TextAttributesKey> = arrayOf(TYPE_IDENTIFIER)
    val KEYWORD_KEYS: Array<TextAttributesKey> = arrayOf(KEYWORD)
    val NUMBER_KEYS: Array<TextAttributesKey> = arrayOf(NUMBER)
    val COMMENT_KEYS: Array<TextAttributesKey> = arrayOf(COMMENT)
    val OPERATOR_KEYS: Array<TextAttributesKey> = arrayOf(OPERATOR)
    val PARENTHESES_KEYS: Array<TextAttributesKey> = arrayOf(PARENTHESES)
    val BRACES_KEYS: Array<TextAttributesKey> = arrayOf(BRACES)
    val SQUARE_BRACKETS_KEYS: Array<TextAttributesKey> = arrayOf(SQUARE_BRACKETS)
  }

  constructor() : super()

  override fun getHighlightingLexer(): Lexer {
    return SppLexerAdaptor()
  }

  override fun getTokenHighlights(tokenType: IElementType?): Array<out TextAttributesKey?> {
    return when (tokenType) {
      in SppTokenSets.KEYWORDS -> KEYWORD_KEYS
      in SppTokenSets.NUMBERS -> NUMBER_KEYS
      in SppTokenSets.COMMENTS -> COMMENT_KEYS
      in SppTokenSets.OPERATORS -> OPERATOR_KEYS
      SppTypes.TOKEN_LEFT_PARENTHESIS, SppTypes.TOKEN_RIGHT_PARENTHESIS -> PARENTHESES_KEYS
      SppTypes.TOKEN_LEFT_CURLY_BRACE, SppTypes.TOKEN_RIGHT_CURLY_BRACE -> BRACES_KEYS
      SppTypes.TOKEN_LEFT_SQUARE_BRACKET, SppTypes.TOKEN_RIGHT_SQUARE_BRACKET -> SQUARE_BRACKETS_KEYS
      SppTypes.LEXEME_IDENTIFIER -> IDENTIFIER_KEYS
      SppTypes.LEXEME_UPPER_IDENTIFIER -> TYPE_IDENTIFIER_KEYS
      else -> EMPTY_KEYS
    }
  }
}