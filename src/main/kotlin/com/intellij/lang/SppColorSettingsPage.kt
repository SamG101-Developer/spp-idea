package com.intellij.lang

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

// The S++ page under "Settings | Editor | Color Scheme". Every
// colour the plugin uses is listed, each on its own key: the
// finer keys fall back to the broader ones (a method call to a
// member, a namespace to a type), so they only differ once given
// a colour of their own here. The preview runs the lexer alone,
// so what the annotators and the compiler's analysis add is marked
// up in the demo text with the tags below.
class SppColorSettingsPage : ColorSettingsPage {
  override fun getIcon(): Icon = SppIcons.FILE

  override fun getHighlighter(): SyntaxHighlighter = SppSyntaxHighlighter()

  override fun getDemoText(): String = DEMO

  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = ATTRIBUTES

  override fun getColorDescriptors(): Array<ColorDescriptor> = COLORS

  override fun getDisplayName(): String = "S++"
}

private val ATTRIBUTES = arrayOf(
  AttributesDescriptor("Keyword", SppSyntaxHighlighter.KEYWORD),
  AttributesDescriptor("Operator", SppSyntaxHighlighter.OPERATOR),
  AttributesDescriptor("Annotation", SppSyntaxHighlighter.ANNOTATION),
  AttributesDescriptor("Bad character", HighlighterColors.BAD_CHARACTER),

  AttributesDescriptor("Identifiers//Default", SppSyntaxHighlighter.IDENTIFIER),
  AttributesDescriptor("Identifiers//Type", SppSyntaxHighlighter.TYPE_IDENTIFIER),
  AttributesDescriptor("Identifiers//Namespace", SppSyntaxHighlighter.NAMESPACE),
  AttributesDescriptor("Identifiers//Compile-time constant (cmp, comp generic)", SppSyntaxHighlighter.CMP_IDENTIFIER),

  AttributesDescriptor("Declarations//Function", SppSyntaxHighlighter.FUNCTION_DECLARATION),
  AttributesDescriptor("Declarations//Coroutine", SppSyntaxHighlighter.COROUTINE_DECLARATION),
  AttributesDescriptor("Declarations//Class attribute", SppSyntaxHighlighter.ATTRIBUTE_DECLARATION),

  AttributesDescriptor("Members and calls//Member (shared default)", SppSyntaxHighlighter.ATTRIBUTE),
  AttributesDescriptor("Members and calls//Field access", SppSyntaxHighlighter.MEMBER_ACCESS),
  AttributesDescriptor("Members and calls//Method call", SppSyntaxHighlighter.METHOD_CALL),
  AttributesDescriptor("Members and calls//Function call", SppSyntaxHighlighter.FUNCTION_CALL),
  AttributesDescriptor("Members and calls//Namespaced function call", SppSyntaxHighlighter.NAMESPACED_CALL),
  AttributesDescriptor("Members and calls//Named argument", SppSyntaxHighlighter.NAMED_ARGUMENT),
  AttributesDescriptor("Members and calls//Pattern field", SppSyntaxHighlighter.PATTERN_FIELD),

  AttributesDescriptor("Literals//Number", SppSyntaxHighlighter.NUMBER),
  AttributesDescriptor("Literals//String", SppSyntaxHighlighter.STRING),
  AttributesDescriptor("Literals//Char", SppSyntaxHighlighter.CHAR),
  AttributesDescriptor("Literals//Escape sequence", SppSyntaxHighlighter.VALID_ESCAPE),

  AttributesDescriptor("Braces and brackets//Shared default", SppSyntaxHighlighter.BRACKET),
  AttributesDescriptor("Braces and brackets//Parentheses", SppSyntaxHighlighter.PARENTHESES),
  AttributesDescriptor("Braces and brackets//Braces", SppSyntaxHighlighter.BRACES),
  AttributesDescriptor("Braces and brackets//Square brackets", SppSyntaxHighlighter.SQUARE_BRACKETS),

  AttributesDescriptor("Comments//Comment", SppSyntaxHighlighter.COMMENT),
  AttributesDescriptor("Comments//Docstring", SppSyntaxHighlighter.DOCSTRING),
  AttributesDescriptor("Comments//Docstring tag", SppSyntaxHighlighter.DOCSTRING_TAG),
  AttributesDescriptor("Comments//Docstring tag value", SppSyntaxHighlighter.DOCSTRING_TAG_VALUE),
  AttributesDescriptor("Comments//Docstring inline code", SppSyntaxHighlighter.DOCSTRING_INLINE_CODE),
  AttributesDescriptor("Comments//Docstring parameter reference", SppSyntaxHighlighter.DOCSTRING_PARAM_REF),

  AttributesDescriptor("Analysis//Code not analysed (after an error)", SppSyntaxHighlighter.UNREACHED_CODE),
  AttributesDescriptor("Analysis//End-of-line error", SppSyntaxHighlighter.INLINE_ERROR),
  AttributesDescriptor("Analysis//End-of-line explanation", SppSyntaxHighlighter.INLINE_EXPLANATION),
  AttributesDescriptor("Analysis//End-of-line compile-time value", SppSyntaxHighlighter.INLINE_CMP_VALUE),
)

private val COLORS = arrayOf(
  ColorDescriptor(
    "Analysis gutter//Not analysed yet",
    SppSyntaxHighlighter.INDEX_PENDING,
    ColorDescriptor.Kind.FOREGROUND
  ),
  ColorDescriptor("Analysis gutter//Analysing", SppSyntaxHighlighter.INDEX_ANALYSING, ColorDescriptor.Kind.FOREGROUND),
  ColorDescriptor("Analysis gutter//Analysed", SppSyntaxHighlighter.INDEX_INDEXED, ColorDescriptor.Kind.FOREGROUND),
  ColorDescriptor(
    "Analysis gutter//Stopped at an error",
    SppSyntaxHighlighter.INDEX_INCOMPLETE,
    ColorDescriptor.Kind.FOREGROUND
  ),
)

private val TAGS = mapOf(
  "annotation" to SppSyntaxHighlighter.ANNOTATION,
  "ns" to SppSyntaxHighlighter.NAMESPACE,
  "cmp" to SppSyntaxHighlighter.CMP_IDENTIFIER,
  "num_suffix" to SppSyntaxHighlighter.NUMBER,
  "fn_decl" to SppSyntaxHighlighter.FUNCTION_DECLARATION,
  "cor_decl" to SppSyntaxHighlighter.COROUTINE_DECLARATION,
  "attr_decl" to SppSyntaxHighlighter.ATTRIBUTE_DECLARATION,
  "field" to SppSyntaxHighlighter.MEMBER_ACCESS,
  "method" to SppSyntaxHighlighter.METHOD_CALL,
  "call" to SppSyntaxHighlighter.FUNCTION_CALL,
  "ns_call" to SppSyntaxHighlighter.NAMESPACED_CALL,
  "named_arg" to SppSyntaxHighlighter.NAMED_ARGUMENT,
  "pattern_field" to SppSyntaxHighlighter.PATTERN_FIELD,
  "string" to SppSyntaxHighlighter.STRING,
  "char" to SppSyntaxHighlighter.CHAR,
  "escape" to SppSyntaxHighlighter.VALID_ESCAPE,
  "doc" to SppSyntaxHighlighter.DOCSTRING,
  "doc_tag" to SppSyntaxHighlighter.DOCSTRING_TAG,
  "doc_tag_value" to SppSyntaxHighlighter.DOCSTRING_TAG_VALUE,
  "doc_code" to SppSyntaxHighlighter.DOCSTRING_INLINE_CODE,
  "doc_ref" to SppSyntaxHighlighter.DOCSTRING_PARAM_REF,
  "unreached" to SppSyntaxHighlighter.UNREACHED_CODE,
  "inline_error" to SppSyntaxHighlighter.INLINE_ERROR,
  "inline_explanation" to SppSyntaxHighlighter.INLINE_EXPLANATION,
  "inline_value" to SppSyntaxHighlighter.INLINE_CMP_VALUE,
)

private val DEMO = """
cls Point {
<attr_decl>x</attr_decl>: S32
<attr_decl>y</attr_decl>: S32
}

sup Point {
<annotation>!public</annotation>
fun <fn_decl>scaled</fn_decl>(&self, factor: S32) -> Point {
<doc># Scales the point by </doc><doc_ref>[factor]</doc_ref><doc>, returning a new </doc><doc_code>`Point`</doc_code><doc>.</doc>
<doc>#</doc>
<doc># </doc><doc_tag>@let</doc_tag> <doc_tag_value>factor</doc_tag_value><doc>: How much to scale each coordinate by.</doc>
ret Point(<named_arg>x</named_arg>=self.<field>x</field> * factor, <named_arg>y</named_arg>=self.<field>y</field> * factor)
}

<annotation>!public</annotation>
cor <cor_decl>coordinates</cor_decl>(&self) -> Gen[S32] {
gen self.<field>x</field>
gen self.<field>y</field>
}
}

fun <fn_decl>repeated</fn_decl>[T: Copy, cmp <cmp>n</cmp>: USize, cmp <cmp>step</cmp>: USize = 1<num_suffix>_uz</num_suffix>](value: T) -> Arr[T, <cmp>n</cmp>] {
ret Arr[T, <cmp>n</cmp>]::<ns_call>new_filled</ns_call>(value)
}

fun main() -> Void {
# An ordinary comment.
let greeting = <ns>std</ns>::<ns>string</ns>::Str::<ns_call>from</ns_call>(<string>"hello</string><escape>\n</escape><string>"</string>)
let initial = <char>'h'</char>
let copy = greeting.<method>clone</method>()
let total = <call>sum</call>([1<num_suffix>_s32</num_suffix>, 2<num_suffix>_s32</num_suffix>, 3<num_suffix>_s32</num_suffix>])
let limit = <ns>std</ns>::<ns>number</ns>::max_s32
cmp <cmp>size</cmp>: USize = 4<num_suffix>_uz</num_suffix><inline_value>    = 4_uz</inline_value>
let buffer = <ns>std</ns>::<ns>array</ns>::Arr[U8, <cmp>size</cmp>]()
let zeros = <call>repeated</call>[S32, 4<num_suffix>_uz</num_suffix>](0<num_suffix>_s32</num_suffix>)
let ones = <call>repeated</call>[T=U8, n=<cmp>size</cmp>, step=2<num_suffix>_uz</num_suffix>](1<num_suffix>_u8</num_suffix>)

case Point(x=1<num_suffix>_s32</num_suffix>, y=2<num_suffix>_s32</num_suffix>) is Point(<pattern_field>x</pattern_field>, ..) {
<unreached>let doubled = x * 2<num_suffix>_s32</num_suffix></unreached>
let first = greeting<inline_explanation>    Moved here</inline_explanation>
let again = greeting<inline_error>    Value used after being moved</inline_error>
}
}
""".trimStart()