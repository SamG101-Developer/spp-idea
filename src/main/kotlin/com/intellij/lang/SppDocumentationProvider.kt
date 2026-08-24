package com.intellij.lang

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.psi.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

private val NUMBERED_ITEM_RE = Regex("""^(\d+)\. """)

private enum class ColorizeState { START, AFTER_FN_KW, AFTER_CLASS_KW, AFTER_NAME, PARAM_NAME, PARAM_TYPE, RETURN_TYPE }

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
            is SppSubroutinePrototype -> docFor(proto.functionImplementation ?: return null, proto.sigText("fun"))
            is SppCoroutinePrototype -> docFor(proto.functionImplementation ?: return null, proto.sigText("cor"))
            is SppClassPrototype -> docFor(proto.classImplementation ?: return null, proto.sigText("cls"))
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
            // Split on whichever comes first: whitespace or colon. This handles both
            // "@ret: description" (colon right after tag) and "@let name: desc" (space first).
            val spIdx = first.indexOfFirst { it.isWhitespace() }
            val colIdx = first.indexOf(':')
            val splitIdx = when {
                spIdx < 0 && colIdx < 0 -> -1
                spIdx < 0 -> colIdx
                colIdx < 0 -> spIdx
                else -> minOf(spIdx, colIdx)
            }
            val tag = if (splitIdx < 0) first else first.substring(0, splitIdx)
            val rest = if (splitIdx < 0) "" else first.substring(splitIdx + 1).trimStart()
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
        var listTag: String? = null  // "ul", "ol", or null
        var inCode = false
        val codeLines = mutableListOf<String>()

        fun closeList() { if (listTag != null) { sb.append("</$listTag>"); listTag = null } }

        fun isListBoundary(s: String) =
            s.isEmpty() || s.startsWith("- ") || s.startsWith("* ") ||
            NUMBERED_ITEM_RE.containsMatchIn(s) || s.startsWith("```")

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Code blocks
            if (trimmed.startsWith("```")) {
                closeList()
                if (!inCode) { inCode = true; codeLines.clear() }
                else {
                    inCode = false
                    sb.append("<pre>").append(codeLines.joinToString("\n").escapeHtml()).append("</pre>")
                    codeLines.clear()
                }
                i++; continue
            }
            if (inCode) { codeLines += line; i++; continue }

            // Blank line (paragraph break)
            if (trimmed.isEmpty()) { closeList(); i++; continue }

            // Unordered bullet list — accumulate continuation lines into the same <li>
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (listTag != "ul") { closeList(); sb.append("<ul>"); listTag = "ul" }
                val item = mutableListOf(trimmed.substring(2))
                i++
                while (i < lines.size) {
                    val next = lines[i].trim()
                    if (isListBoundary(next)) break
                    item += next; i++
                }
                sb.append("<li>").append(renderInline(item.joinToString(" "))).append("</li>")
                continue
            }

            // Ordered (numbered) list — accumulate continuation lines; explicit value= for Swing renderer
            val numMatch = NUMBERED_ITEM_RE.find(trimmed)
            if (numMatch != null) {
                if (listTag != "ol") { closeList(); sb.append("<ol>"); listTag = "ol" }
                val num = numMatch.groupValues[1].toInt()
                val item = mutableListOf(trimmed.substring(numMatch.range.last + 1))
                i++
                while (i < lines.size) {
                    val next = lines[i].trim()
                    if (isListBoundary(next)) break
                    item += next; i++
                }
                sb.append("<li value=\"$num\">").append(renderInline(item.joinToString(" "))).append("</li>")
                continue
            }

            // Standard text paragraph
            closeList()
            val para = mutableListOf(trimmed)
            i++
            while (i < lines.size) {
                val next = lines[i].trim()
                if (isListBoundary(next)) break
                para += next; i++
            }
            sb.append("<p>").append(renderInline(para.joinToString(" "))).append("</p>")
        }

        closeList()
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

    private fun colorizeSignature(sig: String): String {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val identRe = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val tokenRe = Regex("""[A-Za-z_][A-Za-z0-9_]*|->|::|[(){}\[\],:]|\s+|.""")
        val sppKeywords = setOf("fun", "cor", "cls", "sup", "mut", "ref", "let", "self", "Self")

        fun span(text: String, key: TextAttributesKey): String {
            val color = scheme.getAttributes(key)?.foregroundColor
                ?: return text.escapeHtml()
            return "<span style=\"color:#%02x%02x%02x\">${text.escapeHtml()}</span>"
                .format(color.red, color.green, color.blue)
        }

        var state = ColorizeState.START
        var parenDepth = 0
        val sb = StringBuilder()

        for (token in tokenRe.findAll(sig).map { it.value }) {
            if (token.isBlank()) { sb.append(token); continue }
            val out = when (state) {
                ColorizeState.START -> when (token) {
                    "fun", "cor" -> span(token, SppSyntaxHighlighter.KEYWORD).also { state = ColorizeState.AFTER_FN_KW }
                    "cls", "sup" -> span(token, SppSyntaxHighlighter.KEYWORD).also { state = ColorizeState.AFTER_CLASS_KW }
                    else -> token.escapeHtml()
                }
                ColorizeState.AFTER_FN_KW -> if (token.matches(identRe))
                    span(token, SppSyntaxHighlighter.FUNCTION_CALL).also { state = ColorizeState.AFTER_NAME }
                else token.escapeHtml()
                ColorizeState.AFTER_CLASS_KW -> if (token.matches(identRe))
                    span(token, SppSyntaxHighlighter.TYPE_IDENTIFIER).also { state = ColorizeState.AFTER_NAME }
                else token.escapeHtml()
                ColorizeState.AFTER_NAME -> when (token) {
                    "(" -> { parenDepth++; state = ColorizeState.PARAM_NAME; span(token, SppSyntaxHighlighter.BRACKET) }
                    "->", ":" -> { state = ColorizeState.RETURN_TYPE; span(token, SppSyntaxHighlighter.OPERATOR) }
                    else -> token.escapeHtml()
                }
                ColorizeState.PARAM_NAME -> when (token) {
                    "," -> span(token, SppSyntaxHighlighter.OPERATOR)
                    ":" -> { state = ColorizeState.PARAM_TYPE; span(token, SppSyntaxHighlighter.OPERATOR) }
                    "(" -> { parenDepth++; span(token, SppSyntaxHighlighter.BRACKET) }
                    ")" -> { if (--parenDepth == 0) state = ColorizeState.AFTER_NAME; span(token, SppSyntaxHighlighter.BRACKET) }
                    "&" -> span(token, SppSyntaxHighlighter.OPERATOR)
                    else -> when {
                        token in sppKeywords -> span(token, SppSyntaxHighlighter.KEYWORD)
                        else -> token.escapeHtml()
                    }
                }
                ColorizeState.PARAM_TYPE -> when (token) {
                    "," -> { state = ColorizeState.PARAM_NAME; span(token, SppSyntaxHighlighter.OPERATOR) }
                    "(" -> { parenDepth++; span(token, SppSyntaxHighlighter.BRACKET) }
                    ")" -> { if (--parenDepth == 0) state = ColorizeState.AFTER_NAME; span(token, SppSyntaxHighlighter.BRACKET) }
                    "[", "]" -> span(token, SppSyntaxHighlighter.BRACKET)
                    "::", "->", ":", "&" -> span(token, SppSyntaxHighlighter.OPERATOR)
                    else -> when {
                        token in sppKeywords -> span(token, SppSyntaxHighlighter.KEYWORD)
                        token.matches(identRe) -> span(token, SppSyntaxHighlighter.TYPE_IDENTIFIER)
                        else -> token.escapeHtml()
                    }
                }
                ColorizeState.RETURN_TYPE -> when (token) {
                    "(", "[" -> { parenDepth++; span(token, SppSyntaxHighlighter.BRACKET) }
                    ")", "]" -> { parenDepth--; span(token, SppSyntaxHighlighter.BRACKET) }
                    ",", "::", ":", "->", "&" -> span(token, SppSyntaxHighlighter.OPERATOR)
                    else -> when {
                        token in sppKeywords -> span(token, SppSyntaxHighlighter.KEYWORD)
                        token.matches(identRe) -> span(token, SppSyntaxHighlighter.TYPE_IDENTIFIER)
                        else -> token.escapeHtml()
                    }
                }
            }
            sb.append(out)
        }
        return sb.toString()
    }

    private fun buildHtml(signature: String, doc: Docstring): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append(colorizeSignature(signature))
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