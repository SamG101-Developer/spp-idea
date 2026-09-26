package com.intellij.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.psi.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import kotlin.collections.orEmpty

// The docstring annotator validates docstrings, warning about
// invalid tags, keys, or any mismatch between the tags and
// what is being annotated. Docstrings themselves are optional.
class SppDocstringAnnotator : Annotator {
  // A "tag" stores a "tag" (like `@let`), an optional name
  // (like `foo`), and the range of the name in the docstring,
  // if present.
  private data class DocTag(val tag: String, val name: String?, val nameRange: TextRange?)

  // Run general validation on the docstring, and then run specific
  // validation based on the type of element being annotated.
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (element) {
      // Validate the subroutine prototypes' docstrings as a
      // "function docstring".
      is SppSubroutinePrototype -> validateFunctionProto(
        element.functionImplementation ?: return,
        element.functionParameterGroup ?: return,
        element.genericParameterGroup,
        holder
      )

      // Validate the coroutines' prototypes' docstrings as a
      // "function docstring".
      is SppCoroutinePrototype -> validateFunctionProto(
        element.functionImplementation ?: return,
        element.functionParameterGroup ?: return,
        element.genericParameterGroup,
        holder
      )

      // Validate the class prototypes' docstrings as a "class
      // docstring"
      is SppClassPrototype -> validateClassProto(
        element, holder
      )

      // Validate the sup prototypes' docstrings as a "sup
      // docstring"
      is SppSupPrototypeFunctions -> validateSupProto(
        element.supImplementation, holder
      )

      // Validate the sup-ext prototypes' docstrings as a "sup
      // docstring" (with the name, not superclass)
      is SppSupPrototypeExtension -> validateSupProto(
        element.supImplementation, holder
      )
    }
  }

  // Validate a function docstring, by checking for any
  // mismatches between the documented parameters and the actual
  // parameters, and any invalid tags. Support for "@let" (param),
  // "@type" (generic type), and "@cmp" (generic compile) tags is
  // provided. Numerically included lists are validated too.
  // Todo: Ret-type validation for non-Void return types.
  private fun validateFunctionProto(
    impl: SppFunctionImplementation,
    paramGroup: SppFunctionParameterGroup,
    genericGroup: SppGenericParameterGroup?,
    holder: AnnotationHolder,
  ) {
    // Collect the docstring for the function. Docstrings are
    // optional, so an absent one is not itself warned about. Only
    // the content of a docstring that's present gets validated
    // below.
    val docComments = collectDocstringComments(impl)
    if (docComments.isEmpty()) return

    // Perform a validation on any numeric lists in the docstring.
    // This ensures that numbers are in order, and that the first
    // number is 1.
    validateNumberedLists(docComments, holder)

    // Parse the docstring for any tags, and collect the names
    // of the documented parameters, type generics, and comp
    // generics.
    val tags = parseTags(docComments)
    val docLetNames = tags.filter { it.tag == "let" }.mapNotNull { it.name }.toSet()
    val docTypeNames = tags.filter { it.tag == "type" }.mapNotNull { it.name }.toSet()
    val docCmpNames = tags.filter { it.tag == "cmp" }.mapNotNull { it.name }.toSet()

    // Get the actual parameters, type generics and comp generics,
    // so they can be checked against the documented ones for
    // matches / invalid.
    val actualParams = paramGroup.functionParameterList.mapNotNull { paramNameAndElement(it) }
    val actualTypeGenerics = mutableListOf<Pair<String, PsiElement>>()
    val actualCmpGenerics = mutableListOf<Pair<String, PsiElement>>()
    for (gp in genericGroup?.genericParameterList.orEmpty()) {
      genericTypeNameAndElement(gp)?.let { actualTypeGenerics += it }
      genericCompNameAndElement(gp)?.let { actualCmpGenerics += it }
    }

    // Extract the names of the actual asts, to detect mismatches
    // between tags and true definitions.
    val actualParamNames = actualParams.map { it.first }.toSet()
    val actualTypeNames = actualTypeGenerics.map { it.first }.toSet()
    val actualCmpNames = actualCmpGenerics.map { it.first }.toSet()

    // Check for any actual asts that have not been documented.
    for ((name, elem) in actualParams) {
      if (name !in docLetNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Parameter '$name' is not documented - add @let $name")
        .range(elem)
        .create()
    }

    for ((name, elem) in actualTypeGenerics) {
      if (name !in docTypeNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Type generic '$name' is not documented - add @type $name")
        .range(elem)
        .create()
    }

    for ((name, elem) in actualCmpGenerics) {
      if (name !in docCmpNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Comp generic '$name' is not documented - add @cmp $name")
        .range(elem)
        .create()
    }

    // Check for any documented tags that do not have matching
    // asts.
    for (tag in tags.filter { it.tag == "let" && it.name != null && it.name !in actualParamNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No parameter named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()

    for (tag in tags.filter { it.tag == "type" && it.name != null && it.name !in actualTypeNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No type generic parameter named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()

    for (tag in tags.filter { it.tag == "cmp" && it.name != null && it.name !in actualCmpNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No compile-time generic parameter named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()
  }

  // Same validation as the function docstring, but for class
  // docstrings. The only difference is that the parameters are
  // class attributes, not function parameters.
  private fun validateClassProto(proto: SppClassPrototype, holder: AnnotationHolder) {
    val impl = proto.classImplementation ?: return
    val genericGroup = proto.genericParameterGroup

    val docComments = collectDocstringComments(impl)
    if (docComments.isEmpty()) return
    validateNumberedLists(docComments, holder)

    val tags = parseTags(docComments)
    val docAttNames = tags.filter { it.tag == "let" }.mapNotNull { it.name }.toSet()
    val docTypeNames = tags.filter { it.tag == "type" }.mapNotNull { it.name }.toSet()
    val docCmpNames = tags.filter { it.tag == "cmp" }.mapNotNull { it.name }.toSet()

    val actualAttrs =
      impl.classMemberList.map { it.classAttribute }.map { Pair(it.identifier.text, it.identifier as PsiElement) }

    val actualTypeGenerics = mutableListOf<Pair<String, PsiElement>>()
    val actualCmpGenerics = mutableListOf<Pair<String, PsiElement>>()
    for (gp in genericGroup?.genericParameterList.orEmpty()) {
      genericTypeNameAndElement(gp)?.let { actualTypeGenerics += it }
      genericCompNameAndElement(gp)?.let { actualCmpGenerics += it }
    }

    val actualAttrNames = actualAttrs.map { it.first }.toSet()
    val actualTypeNames = actualTypeGenerics.map { it.first }.toSet()
    val actualCmpNames = actualCmpGenerics.map { it.first }.toSet()

    for ((name, elem) in actualAttrs) {
      if (name !in docAttNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Attribute '$name' is not documented: add @let $name")
        .range(elem)
        .create()
    }

    for ((name, elem) in actualTypeGenerics) {
      if (name !in docTypeNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Generic type '$name' is not documented: add @type $name")
        .range(elem)
        .create()
    }

    for ((name, elem) in actualCmpGenerics) {
      if (name !in docCmpNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Compile-time generic '$name' is not documented: add @cmp $name")
        .range(elem)
        .create()
    }

    for (tag in tags.filter { it.tag == "let" && it.name != null && it.name !in actualAttrNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No attribute named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()

    for (tag in tags.filter { it.tag == "type" && it.name != null && it.name !in actualTypeNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No type generic parameter named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()

    for (tag in tags.filter { it.tag == "cmp" && it.name != null && it.name !in actualCmpNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No compile-time generic parameter named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()
  }

  // Same validation as the function docstring, but for sup
  // docstrings. The only difference is that there are no
  // "members" of a supo-block that are documented at the
  // sup-level. They would have their own docstrings.
  private fun validateSupProto(impl: SppSupImplementation, holder: AnnotationHolder) {
    val docComments = collectDocstringComments(impl)
    if (docComments.isEmpty()) return
    validateNumberedLists(docComments, holder)

    val tags = parseTags(docComments)
    val docTypeNames = tags.filter { it.tag == "type" }.mapNotNull { it.name }.toSet()
    val docCmpNames = tags.filter { it.tag == "cmp" }.mapNotNull { it.name }.toSet()

    val actualTypeStatements = impl.supMemberList.mapNotNull { it.supTypeStatement }
      .map { Pair(it.typeStatement.upperIdentifier.text, it.typeStatement.upperIdentifier as PsiElement) }
    val actualCmpStatements = impl.supMemberList.mapNotNull { it.supCmpStatement }
      .map { Pair(it.cmpStatement.identifier.text, it.cmpStatement.identifier as PsiElement) }

    val actualTypeNames = actualTypeStatements.map { it.first }.toSet()
    val actualCmpNames = actualCmpStatements.map { it.first }.toSet()

    for ((name, elem) in actualTypeStatements) {
      if (name !in docTypeNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Type '$name' is not documented: add @type $name")
        .range(elem)
        .create()
    }

    for ((name, elem) in actualCmpStatements) {
      if (name !in docCmpNames) holder
        .newAnnotation(HighlightSeverity.WARNING, "Constant '$name' is not documented: add @cmp $name")
        .range(elem)
        .create()
    }

    for (tag in tags.filter { it.tag == "type" && it.name != null && it.name !in actualTypeNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No type named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()

    for (tag in tags.filter { it.tag == "cmp" && it.name != null && it.name !in actualCmpNames }) holder
      .newAnnotation(HighlightSeverity.WARNING, "No constant named '${tag.name}'")
      .range(tag.nameRange!!)
      .create()
  }

  // Regex to match a numbered list item, e.g. "1. ", "2. ",
  // etc.
  private val numberedItemRe = Regex("""^(\d+)\. """)

  // The validation for numbered lists ensures that the
  // first number is 1, and that each subsequent number
  // is incremented by 1. A blank line resets the expected
  // number, allowing for multiline text following a number.
  private fun validateNumberedLists(comments: List<PsiComment>, holder: AnnotationHolder) {
    var expectedNum = -1  // -1 = not currently inside a list
    for (comment in comments) {
      val rawText = comment.text
      val stripped = rawText.removePrefix("#").let { if (it.startsWith(" ")) it.substring(1) else it }
      val prefixLen = rawText.length - stripped.length

      if (stripped.trimStart().startsWith("@")) break  // entered tag section

      val match = numberedItemRe.find(stripped)
      if (match != null) {
        val num = match.groupValues[1].toInt()
        val numRange = TextRange(
          comment.textRange.startOffset + prefixLen + match.groups[1]!!.range.first,
          comment.textRange.startOffset + prefixLen + match.groups[1]!!.range.last + 1,
        )
        if (expectedNum == -1) {
          if (num != 1) holder
            .newAnnotation(HighlightSeverity.WARNING, "Numbered list should start at 1, not $num")
            .range(numRange)
            .create()
        } else if (num != expectedNum) {
          holder
            .newAnnotation(HighlightSeverity.WARNING, "Expected list item $expectedNum, got $num")
            .range(numRange)
            .create()
        }
        expectedNum = num + 1
      } else {
        // A blank line only, resets the expected number.
        // Allows for multiline text following a number.
        if (stripped.isBlank()) expectedNum = -1
      }
    }
  }

  // Regex to match a docstring tag, e.g. "@let foo", "@type T",
  // "@cmp BAR", etc. The tag is captured in group 1, and the
  // optional name is captured in group 2.
  private val tagPattern = Regex("""@(\w+)(?:\s+([^\s:]+))?""")

  // Parse the docstring comments for any tags, returning a list
  // of DocTag objects. Each DocTag contains the tag, the optional
  // name, and the range of the name in the docstring, if present.
  private fun parseTags(comments: List<PsiComment>): List<DocTag> {
    val result = mutableListOf<DocTag>()
    for (comment in comments) {
      val base = comment.textRange.startOffset
      for (match in tagPattern.findAll(comment.text)) {
        val tag = match.groups[1]!!.value
        val nameGroup = match.groups[2]
        result += DocTag(
          tag = tag,
          name = nameGroup?.value?.trimStart { !it.isLetterOrDigit() && it != '_' },
          nameRange = nameGroup?.let { TextRange(base + it.range.first, base + it.range.last + 1) })
      }
    }
    return result
  }

  // Collect the docstring comments for a given implementation.
  // The docstring comments are the comments that immediately
  // follow the opening brace of the implementation, and are
  // not separated by a blank line. The docstring comments are
  // returned in the order they appear in the implementation.
  private fun collectDocstringComments(impl: PsiElement): List<PsiComment> {
    val result = mutableListOf<PsiComment>()
    var child = impl.firstChild?.nextSibling // step past `{`
    while (child != null) {
      when (child) {
        is PsiComment -> result += child
        is PsiWhiteSpace if !child.text.contains("\n\n") -> {}
        else -> break
      }
      child = child.nextSibling
    }
    return result
  }

  // Extract the name and element of a function parameter, if
  // it has one. The name is the identifier of the parameter,
  // and the element is the PsiElement of the identifier. If
  // the parameter is a "self" parameter, it has no name and
  // no element, so null is returned.
  private fun paramNameAndElement(param: SppFunctionParameter): Pair<String, PsiElement>? {
    val localVar = param.functionParameterRequired?.localVariable
      ?: param.functionParameterOptional?.localVariable
      ?: param.functionParameterVariadic?.localVariable ?: return null
    val single = localVar.localVariableSingleIdentifier ?: return null
    val ident = single.identifier
    return Pair(ident.text, ident)
  }

  // Extract the name and element of a generic type parameter.
  // The name is the identifier of the parameter, and the element
  // is the PsiElement of the identifier.
  private fun genericTypeNameAndElement(param: SppGenericParameter): Pair<String, PsiElement>? {
    val gType = param.genericParameterType ?: return null
    val typeId = gType.genericParameterTypeRequired?.typeIdentifier
      ?: gType.genericParameterTypeOptional?.typeIdentifier
      ?: gType.genericParameterTypeVariadic?.typeIdentifier
      ?: return null
    return Pair(typeId.text, typeId)
  }

  // Extract the name and element of a generic comp parameter.
  // The name is the identifier of the parameter, and the element
  // is the PsiElement of the identifier.
  private fun genericCompNameAndElement(param: SppGenericParameter): Pair<String, PsiElement>? {
    val gComp = param.genericParameterComp ?: return null
    val id = gComp.genericParameterCompRequired?.identifier
      ?: gComp.genericParameterCompOptional?.identifier
      ?: gComp.genericParameterCompVariadic?.identifier
      ?: return null
    return Pair(id.text, id)
  }
}