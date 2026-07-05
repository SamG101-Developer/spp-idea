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

/**
 * The `SppDocstringAnnotator` provides the default docstring templates, and warning for missing or invalid docstrings.
 * It hooks into the "tag" usage system, using things like `\@let` etc. There are
 */
class SppDocstringAnnotator : Annotator {

    private data class DocTag(val tag: String, val name: String?, val nameRange: TextRange?)

    /**
     * Analyse the given docstring on the element and create any warnings based on missing docstrings or invalid tags
     * provided in the docstring.
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            // Validate the subroutine prototypes' docstrings as a "function docstring"
            is SppSubroutinePrototype -> validateFunctionProto(
                element.functionImplementation,
                element.functionParameterGroup,
                element.genericParameterGroup,
                element.identifier,
                holder
            )

            // Validate the coroutines' prototypes' docstrings as a "function docstring"
            is SppCoroutinePrototype -> validateFunctionProto(
                element.functionImplementation,
                element.functionParameterGroup,
                element.genericParameterGroup,
                element.identifier,
                holder
            )

            // Validate the class prototypes' docstrings as a "class docstring"
            is SppClassPrototype -> validateClassProto(element, holder)

            // Validate the sup prototypes' docstrings as a "sup docstring"
            is SppSupPrototypeFunctions -> validateSupProto(
                element.supImplementation, element.typeExpression, holder
            )

            // Validate the sup-ext prototypes' docstrings as a "sup docstring" (with the name, not superclass)
            is SppSupPrototypeExtension -> validateSupProto(
                element.supImplementation, element.typeExpressionList.first(), holder
            )
        }
    }

    /**
     * Validate the function docstrings, with the possible tags:
     * - `\@let` -> function parameters
     * - `\@type` -> generic type parameters
     * - `\@comp` -> generic comp parameters
     *
     * Todo: The return type is not validated yet, but will have the following future validation. If Void, omit it and
     *  require it be omitted, otherwise require it to be included. This will require the LSP, for aliases against
     *  `Void`.
     */
    private fun validateFunctionProto(
        impl: SppFunctionImplementation,
        paramGroup: SppFunctionParameterGroup,
        genericGroup: SppGenericParameterGroup?,
        nameElem: PsiElement,
        holder: AnnotationHolder,
    ) {
        // Collect the docstring for the function, and create a warning if it is empty - all functions should be
        // documented. Todo: Create a "builtin" annotation that can avoid this ie "!nodoc(reason: StrView)" or similar.
        val docComments = collectDocstringComments(impl)
        if (docComments.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Missing docstring").range(nameElem).create()
            return
        }

        // Perform a validation on any numeric lists in the docstring.
        validateNumberedLists(docComments, holder)

        // Parse the docstring for any tags, and collect the names of the documented parameters, type generics, and comp
        // generics.
        val tags = parseTags(docComments)
        val docLetNames = tags.filter { it.tag == "let" }.mapNotNull { it.name }.toSet()
        val docTypeNames = tags.filter { it.tag == "type" }.mapNotNull { it.name }.toSet()
        val docCmpNames = tags.filter { it.tag == "cmp" }.mapNotNull { it.name }.toSet()

        // Get the actual parameters, type generics and comp generics, so they can be checked against the documented
        // ones for matches / invalid.
        val actualParams = paramGroup.functionParameterList.mapNotNull { paramNameAndElement(it) }
        val actualTypeGenerics = mutableListOf<Pair<String, PsiElement>>()
        val actualCmpGenerics = mutableListOf<Pair<String, PsiElement>>()
        for (gp in genericGroup?.genericParameterList.orEmpty()) {
            genericTypeNameAndElement(gp)?.let { actualTypeGenerics += it }
            genericCompNameAndElement(gp)?.let { actualCmpGenerics += it }
        }

        // Extract the names of the actual asts.
        val actualParamNames = actualParams.map { it.first }.toSet()
        val actualTypeNames = actualTypeGenerics.map { it.first }.toSet()
        val actualCmpNames = actualCmpGenerics.map { it.first }.toSet()

        // Check for any actual asts that have not been documented.
        for ((name, elem) in actualParams) {
            if (name !in docLetNames) holder.newAnnotation(
                HighlightSeverity.WARNING, "Parameter '$name' is not documented - add @let $name to the docstring"
            ).range(elem).create()
        }
        for ((name, elem) in actualTypeGenerics) {
            if (name !in docTypeNames) holder.newAnnotation(
                HighlightSeverity.WARNING, "Type generic '$name' is not documented - add @type $name to the docstring"
            ).range(elem).create()
        }
        for ((name, elem) in actualCmpGenerics) {
            if (name !in docCmpNames) holder.newAnnotation(
                HighlightSeverity.WARNING, "Comp generic '$name' is not documented - add @cmp $name to the docstring"
            ).range(elem).create()
        }

        // Check for any documented tags that do not have matching asts.
        for (tag in tags.filter { it.tag == "let" && it.name != null && it.name !in actualParamNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No parameter named '${tag.name}'"
        ).range(tag.nameRange!!).create()
        for (tag in tags.filter { it.tag == "type" && it.name != null && it.name !in actualTypeNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No type generic parameter named '${tag.name}'"
        ).range(tag.nameRange!!).create()
        for (tag in tags.filter { it.tag == "cmp" && it.name != null && it.name !in actualCmpNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No compile-time generic parameter named '${tag.name}'"
        ).range(tag.nameRange!!).create()
    }

    private fun validateClassProto(proto: SppClassPrototype, holder: AnnotationHolder) {
        val impl = proto.classImplementation
        val genericGroup = proto.genericParameterGroup

        val docComments = collectDocstringComments(impl)
        if (docComments.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Missing docstring").range(proto.upperIdentifier).create()
            return
        }
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
            if (name !in docAttNames) holder.newAnnotation(
                HighlightSeverity.WARNING, "Attribute '$name' is not documented — add @let $name to the docstring"
            ).range(elem).create()
        }
        for ((name, elem) in actualTypeGenerics) {
            if (name !in docTypeNames) holder.newAnnotation(
                HighlightSeverity.WARNING, "Generic type '$name' is not documented — add @type $name to the docstring"
            ).range(elem).create()
        }
        for ((name, elem) in actualCmpGenerics) {
            if (name !in docCmpNames) holder.newAnnotation(
                HighlightSeverity.WARNING,
                "Compile-time generic '$name' is not documented — add @cmp $name to the docstring"
            ).range(elem).create()
        }

        for (tag in tags.filter { it.tag == "let" && it.name != null && it.name !in actualAttrNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No attribute named '${tag.name}'"
        ).range(tag.nameRange!!).create()
        for (tag in tags.filter { it.tag == "type" && it.name != null && it.name !in actualTypeNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No type generic parameter named '${tag.name}'"
        ).range(tag.nameRange!!).create()
        for (tag in tags.filter { it.tag == "cmp" && it.name != null && it.name !in actualCmpNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No compile-time generic parameter named '${tag.name}'"
        ).range(tag.nameRange!!).create()
    }

    private fun validateSupProto(
        impl: SppSupImplementation,
        nameElem: PsiElement,
        holder: AnnotationHolder,
    ) {
        val docComments = collectDocstringComments(impl)
        if (docComments.isEmpty()) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Missing docstring").range(nameElem).create()
            return
        }
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
            if (name !in docTypeNames) holder.newAnnotation(
                HighlightSeverity.WARNING, "Type '$name' is not documented — add @type $name to the docstring"
            ).range(elem).create()
        }
        for ((name, elem) in actualCmpStatements) {
            if (name !in docCmpNames) holder.newAnnotation(
                HighlightSeverity.WARNING, "Constant '$name' is not documented — add @cmp $name to the docstring"
            ).range(elem).create()
        }

        for (tag in tags.filter { it.tag == "type" && it.name != null && it.name !in actualTypeNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No type named '${tag.name}'"
        ).range(tag.nameRange!!).create()
        for (tag in tags.filter { it.tag == "cmp" && it.name != null && it.name !in actualCmpNames }) holder.newAnnotation(
            HighlightSeverity.WARNING, "No constant named '${tag.name}'"
        ).range(tag.nameRange!!).create()
    }

    private val numberedItemRe = Regex("""^(\d+)\. """)

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
                    if (num != 1) holder.newAnnotation(
                        HighlightSeverity.WARNING, "Numbered list should start at 1, not $num"
                    ).range(numRange).create()
                } else if (num != expectedNum) {
                    holder.newAnnotation(
                        HighlightSeverity.WARNING, "Expected list item $expectedNum, got $num"
                    ).range(numRange).create()
                }
                expectedNum = num + 1
            } else {
                // A blank line only, resets the expected number. Allows for multiline text following a number.
                if (stripped.isBlank()) expectedNum = -1
            }
        }
    }

    private val tagPattern = Regex("""@(\w+)(?:\s+([^\s:]+))?""")

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

    private fun paramNameAndElement(param: SppFunctionParameter): Pair<String, PsiElement>? {
        val localVar = param.functionParameterRequired?.localVariable ?: param.functionParameterOptional?.localVariable
        ?: param.functionParameterVariadic?.localVariable ?: return null // self parameter — no doc tag needed
        val single = localVar.localVariableSingleIdentifier ?: return null
        // Prefer the alias (internal name used in the body) if one is declared, e.g. `pub as priv: Type`
        val ident = single.localVariableSingleIdentifierAlias?.identifier ?: single.identifier
        return Pair(ident.text, ident)
    }

    private fun genericTypeNameAndElement(param: SppGenericParameter): Pair<String, PsiElement>? {
        val gType = param.genericParameterType ?: return null
        val typeId =
            gType.genericParameterTypeRequired?.typeIdentifier ?: gType.genericParameterTypeOptional?.typeIdentifier
            ?: gType.genericParameterTypeVariadic?.typeIdentifier ?: return null
        return Pair(typeId.text, typeId)
    }

    private fun genericCompNameAndElement(param: SppGenericParameter): Pair<String, PsiElement>? {
        val gComp = param.genericParameterComp ?: return null
        val id = gComp.genericParameterCompRequired?.identifier ?: gComp.genericParameterCompOptional?.identifier
        ?: gComp.genericParameterCompVariadic?.identifier ?: return null
        return Pair(id.text, id)
    }
}