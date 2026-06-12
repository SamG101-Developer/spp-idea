package com.intellij.lang

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.psi.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

class SppDocumentationProvider : AbstractDocumentationProvider() {

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? = contextElement?.let { findProto(it) }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element ?: originalElement ?: return null
        return when (val proto = findProto(target)) {
            is SppSubroutinePrototype -> docFor(proto.functionImplementation, proto.sigText("fun"))
            is SppCoroutinePrototype -> docFor(proto.functionImplementation, proto.sigText("cor"))
            is SppClassPrototype -> docFor(proto.classImplementation, proto.sigText("cls"))
            is SppSupPrototypeFunctions -> docFor(proto.supImplementation, proto.sigText("sup"))
            is SppSupPrototypeExtension -> docFor(proto.supImplementation, proto.sigText("sup"))
            else -> null
        }
    }

    private fun findProto(element: PsiElement): PsiElement? {
        var e: PsiElement? = element
        // Walk up the PDI tree to find the nearest prototype.
        while (e != null) {
            when (e) {
                is SppFunctionImplementation,
                is SppClassImplementation,
                is SppSupImplementation -> {
                    val proto = e.parent ?: return null
                    val isDocComment = element is PsiComment && element in collectDocstringComments(e)
                    return if (isDocComment) proto else null
                }

                is SppSubroutinePrototype,
                is SppCoroutinePrototype,
                is SppClassPrototype,
                is SppSupPrototypeFunctions,
                is SppSupPrototypeExtension -> return e
            }
            e = e.parent
        }
        return null
    }

    private fun docFor(impl: PsiElement, signature: String): String? {
        // Build the HTML doc for a psi element.
        val comments = collectDocstringComments(impl)
        if (comments.isEmpty()) return null
        return buildHtml(signature, parseDocstring(comments))
    }

    private fun PsiElement.sigText(keyword: String): String {
        // Get the signature of the psi element.
        val idx = text.indexOf("$keyword ")
        return (if (idx >= 0) text.substring(idx) else text).substringBefore("{").trim()
    }

    private fun collectDocstringComments(impl: PsiElement): List<PsiComment> {
        val result = mutableListOf<PsiComment>()
        var child = impl.firstChild?.nextSibling
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

    private data class Docstring(
        val bodyHtml: String,
        val params: List<Pair<String, String>>,
        val types: List<Pair<String, String>>,
        val cmps: List<Pair<String, String>>,
        val ret: String?,
    )

    private fun parseDocstring(comments: List<PsiComment>): Docstring {
        // Strip `# ` prefix from each comment.
        val lines = comments.map { c ->
            val t = c.text.removePrefix("#")
            if (t.startsWith(" ")) t.substring(1) else t
        }

        // Partition: body lines come before the first @-tag (outside code fences).
        // Each @-tag line and its non-@ continuations form a tag group.
        val bodyLines = mutableListOf<String>()
        val tagGroups = mutableListOf<MutableList<String>>()
        var inCodeBlock = false
        var sawTag = false

        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
            }

            if (!inCodeBlock && !sawTag && trimmed.startsWith("@")) sawTag = true

            when {
                !sawTag -> bodyLines += line
                trimmed.startsWith("@") && !inCodeBlock -> tagGroups += mutableListOf(line)
                tagGroups.isNotEmpty() -> tagGroups.last() += line
                else -> bodyLines += line
            }
        }

        // Decode each tag group into its tag name, parameter name, and description.
        val params = mutableListOf<Pair<String, String>>()
        val types = mutableListOf<Pair<String, String>>()
        val cmps = mutableListOf<Pair<String, String>>()
        var ret: String? = null

        for (group in tagGroups) {
            val first = group.first().trimStart().removePrefix("@")
            val spIdx = first.indexOfFirst { it.isWhitespace() }
            val tag = if (spIdx < 0) first else first.substring(0, spIdx)
            val rest = if (spIdx < 0) "" else first.substring(spIdx + 1).trimStart()
            val conts = group.drop(1).joinToString(" ") { it.trim() }
            val full = if (conts.isBlank()) rest else "$rest $conts".trim()

            when (tag) {
                "let" -> params += nameAndDesc(full)
                "type" -> types += nameAndDesc(full)
                "cmp" -> cmps += nameAndDesc(full)
                "ret" -> ret = renderInline(full.removePrefix(":").trimStart())
            }
        }

        return Docstring(renderBody(bodyLines), params, types, cmps, ret)
    }

    /** Splits a tag's payload `?name: description` into the bare name and the inline-rendered description. */
    private fun nameAndDesc(s: String): Pair<String, String> {
        val colon = s.indexOf(':')
        return if (colon < 0) {
            Pair(s.trimStart { !it.isLetterOrDigit() && it != '_' }, "")
        } else {
            val name = s.substring(0, colon).trimStart { !it.isLetterOrDigit() && it != '_' }
            val desc = s.substring(colon + 1).trimStart()
            Pair(name, renderInline(desc))
        }
    }

    private fun renderBody(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        var inList = false
        var inCode = false
        val codeLines = mutableListOf<String>()

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Code blocks
            if (trimmed.startsWith("```")) {
                if (inList) {
                    sb.append("</ul>"); inList = false
                }
                if (!inCode) {
                    inCode = true; codeLines.clear()
                } else {
                    inCode = false
                    sb.append("<pre>").append(codeLines.joinToString("\n").escapeHtml()).append("</pre>")
                    codeLines.clear()
                }
                i++; continue
            }
            if (inCode) {
                codeLines += line; i++; continue
            }

            // Blank line (paragraph break)
            if (trimmed.isEmpty()) {
                if (inList) {
                    sb.append("</ul>"); inList = false
                }
                i++; continue
            }

            // Bullet point list
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    sb.append("<ul>"); inList = true
                }
                sb.append("<li>").append(renderInline(trimmed.substring(2))).append("</li>")
                i++; continue
            }

            // Standard text paragraph
            if (inList) {
                sb.append("</ul>"); inList = false
            }
            val para = mutableListOf(trimmed)
            i++
            while (i < lines.size) {
                val next = lines[i].trim()
                if (next.isEmpty() || next.startsWith("- ") || next.startsWith("* ") ||
                    next.startsWith("```")
                ) break
                para += next; i++
            }
            sb.append("<p>").append(renderInline(para.joinToString(" "))).append("</p>")
        }

        if (inList) sb.append("</ul>")
        if (inCode && codeLines.isNotEmpty())
            sb.append("<pre>").append(codeLines.joinToString("\n").escapeHtml()).append("</pre>")
        return sb.toString()
    }

    /** Converts `` `code` `` spans to `<code>` and escapes HTML everywhere else. */
    private fun renderInline(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    sb.append("<code>").append(text.substring(i + 1, end).escapeHtml()).append("</code>")
                    i = end + 1
                } else {
                    sb.append('`'); i++
                }
            } else {
                sb.append(text[i].escapeHtml()); i++
            }
        }
        return sb.toString()
    }

    private fun buildHtml(signature: String, doc: Docstring): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append(signature.escapeHtml())
        append(DocumentationMarkup.DEFINITION_END)

        append(DocumentationMarkup.CONTENT_START)
        append(doc.bodyHtml)

        val hasSections = doc.params.isNotEmpty() || doc.types.isNotEmpty() ||
                doc.cmps.isNotEmpty() || doc.ret != null
        if (hasSections) {
            append(DocumentationMarkup.SECTIONS_START)
            appendSection("Parameters", doc.params)
            appendSection("Type parameters", doc.types)
            appendSection("Compile-time parameters", doc.cmps)
            if (doc.ret != null) {
                append(DocumentationMarkup.SECTION_HEADER_START)
                append("Returns")
                append(DocumentationMarkup.SECTION_SEPARATOR)
                append(doc.ret)
                append(DocumentationMarkup.SECTION_END)
            }
            append(DocumentationMarkup.SECTIONS_END)
        }

        append(DocumentationMarkup.CONTENT_END)
    }

    private fun StringBuilder.appendSection(title: String, entries: List<Pair<String, String>>) {
        if (entries.isEmpty()) return
        append(DocumentationMarkup.SECTION_HEADER_START)
        append(title)
        append(DocumentationMarkup.SECTION_SEPARATOR)
        for ((name, html) in entries)
            append("<p><code>$name</code> — $html</p>")
        append(DocumentationMarkup.SECTION_END)
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun Char.escapeHtml(): String = when (this) {
        '<' -> "&lt;"
        '>' -> "&gt;"
        '&' -> "&amp;"
        '"' -> "&quot;"
        else -> toString()
    }
}