package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * Turning the compiler's line-and-column answers into editor offsets, and back to the name under a caret.
 *
 * The compiler counts columns in UTF-16 code units, which is what a [Document] counts offsets in, so the two agree
 * without conversion; all that is left is the line start.
 */
object SppSymbolLookup {

    /** The span as a range in [document], or null when it falls outside what the editor holds. */
    fun rangeOf(span: SppSpan, document: Document): TextRange? {
        if (span.generated || span.startLine < 0 || span.startLine >= document.lineCount) return null
        val lineStart = document.getLineStartOffset(span.startLine)
        val lineEnd = document.getLineEndOffset(span.startLine)
        val start = (lineStart + span.startCharacter).coerceIn(lineStart, lineEnd)
        val end = when {
            span.endLine != span.startLine -> lineEnd
            else -> (lineStart + span.endCharacter).coerceIn(start, lineEnd)
        }
        return if (end > start) TextRange(start, end) else null
    }

    /** The offset a span starts at in [document], or null when it has no place there. */
    fun offsetOf(span: SppSpan, document: Document): Int? {
        if (span.generated || span.startLine < 0 || span.startLine >= document.lineCount) return null
        val lineStart = document.getLineStartOffset(span.startLine)
        return (lineStart + span.startCharacter).coerceAtMost(document.getLineEndOffset(span.startLine))
    }

    /**
     * The name written at [offset], if any. The smallest match wins: a type's arguments are written inside the type,
     * so the caret sits in several spans at once and the innermost is the one being pointed at.
     */
    /**
     * What is being completed at [offset]: the access written just before it, and the part of the name typed so far.
     *
     * A completion is asked for after a "." or a "::", possibly with some of the name already typed. Reading the text
     * backwards - over the part typed, over any spaces, then over the access itself - says which of the two was
     * written and where the thing being accessed ends.
     */
    fun accessBefore(text: CharSequence, offset: Int): Access? {
        var at = offset.coerceIn(0, text.length)

        // The part of the name already typed, which is what the offered list is filtered by.
        val prefixEnd = at
        while (at > 0 && (text[at - 1].isLetterOrDigit() || text[at - 1] == '_')) at--
        val prefix = text.subSequence(at, prefixEnd).toString()

        while (at > 0 && text[at - 1].isWhitespace()) at--
        val operator = when {
            at >= 2 && text[at - 1] == ':' && text[at - 2] == ':' -> "::"
            at >= 1 && text[at - 1] == '.' -> "."
            else -> return null
        }

        at -= operator.length
        while (at > 0 && text[at - 1].isWhitespace()) at--
        return Access(operator, receiverEnd = at, prefix = prefix)
    }

    /**
     * What is being accessed: whatever ends exactly where the access begins.
     *
     * Several things can end there - "v.take_last" the method and "v.take_last()" the call end one character apart,
     * while a name and the expression around it end together - so the widest is taken, which is the whole of what the
     * access is applied to rather than its last part.
     */
    fun receiverOf(symbols: List<SppSymbol>, access: Access, document: Document): SppSymbol? = symbols
        .mapNotNull { symbol -> rangeOf(symbol.use, document)?.let { symbol to it } }
        .filter { (_, range) -> range.endOffset == access.receiverEnd }
        .maxByOrNull { (_, range) -> range.length }
        ?.first

    /**
     * A type as it is worth showing beside a completion: every name in it cut down to its last part, and nothing else
     * touched.
     *
     * Cutting at the last "::" of the whole string does not do this. It loses the convention a borrowed type starts
     * with, turning "&Str" into "Str" - which is the opposite of what it says - and on a generic type the last "::"
     * is inside the brackets, so "Vec[T=std::number::S32]" comes out as "S32]".
     */
    fun shortTypeName(type: String): String =
        QUALIFIED_NAME.replace(type) { it.value.substringAfterLast("::") }

    private val QUALIFIED_NAME = Regex("""[A-Za-z_][A-Za-z0-9_]*(?:::[A-Za-z_][A-Za-z0-9_]*)+""")

    /**
     * The parameters a caller actually writes, taken from a recorded signature.
     *
     * A method's first parameter is its receiver - "self", "&self", "&mut self" - which is what it is called on
     * rather than something passed to it, so it is not part of what the brackets are for.
     */
    fun callParameters(signature: String): List<String> {
        val inside = signature.substringBeforeLast(") ->").removePrefix("(")
        return inside.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterIndexed { i, param -> i > 0 || !RECEIVER.matches(param) }
    }

    /** Whether a call to this signature is written with anything between its brackets. */
    fun callTakesArguments(signature: String): Boolean = callParameters(signature).isNotEmpty()

    private val RECEIVER = Regex("""&?(?:mut )?self""")

    /** The part of a name typed before [offset], which is what an offered list is filtered by. */
    fun prefixBefore(text: CharSequence, offset: Int): String {
        var at = offset.coerceIn(0, text.length)
        val end = at
        while (at > 0 && (text[at - 1].isLetterOrDigit() || text[at - 1] == '_')) at--
        return text.subSequence(at, end).toString()
    }

    /**
     * The bracket the caret is inside, if any: read backwards, counting closed brackets off against open ones, so
     * that "f(g(x), " answers with the "(" of "f" rather than the one of "g".
     */
    fun enclosingOpenBracket(text: CharSequence, offset: Int): Int? {
        var depth = 0
        for (at in offset.coerceIn(0, text.length) - 1 downTo 0) {
            when (text[at]) {
                ')' -> depth++
                '(' -> if (depth == 0) return at else depth--
                ';', '{', '}' -> return null
                else -> {}
            }
        }
        return null
    }

    /** Where the thing before an open bracket ends: the name of what is being called or built. */
    fun calleeEnd(text: CharSequence, openBracket: Int): Int {
        var at = openBracket
        while (at > 0 && text[at - 1].isWhitespace()) at--
        return at
    }

    /** Everything nameable at [offset]: what every scope covering it holds, innermost last. */
    fun namesInScopeAt(scopes: List<SppScope>, offset: Int, document: Document): List<SppMember> = scopes
        .mapNotNull { scope -> regionOf(scope.where, document)?.let { scope to it } }
        .filter { (_, region) -> offset >= region.first && offset <= region.second }
        .sortedByDescending { (_, region) -> region.second - region.first }
        .flatMap { (scope, _) -> scope.names }

    /** The call whose arguments [offset] sits in, innermost first. */
    fun signatureAt(signatures: List<SppSignature>, offset: Int, document: Document): SppSignature? = signatures
        .mapNotNull { signature -> regionOf(signature.arguments, document)?.let { signature to it } }
        .filter { (_, region) -> offset >= region.first && offset <= region.second }
        .minByOrNull { (_, region) -> region.second - region.first }
        ?.first

    /** A region that may cross lines, as a pair of offsets; null when it falls outside the document. */
    private fun regionOf(span: SppSpan, document: Document): Pair<Int, Int>? {
        if (span.generated || span.startLine < 0 || span.startLine >= document.lineCount) return null
        if (span.endLine < span.startLine || span.endLine >= document.lineCount) return null
        val start = (document.getLineStartOffset(span.startLine) + span.startCharacter)
            .coerceAtMost(document.getLineEndOffset(span.startLine))
        val end = (document.getLineStartOffset(span.endLine) + span.endCharacter)
            .coerceAtMost(document.getLineEndOffset(span.endLine))
        return if (end >= start) start to end else null
    }

    /** An access written before a caret: which operator, where what it is applied to ends, and what has been typed. */
    data class Access(val operator: String, val receiverEnd: Int, val prefix: String)

    fun symbolAt(symbols: List<SppSymbol>, offset: Int, document: Document): SppSymbol? = symbols
        .mapNotNull { symbol -> rangeOf(symbol.use, document)?.let { symbol to it } }
        .filter { (_, range) -> offset >= range.startOffset && offset <= range.endOffset }
        .minByOrNull { (_, range) -> range.length }
        ?.first
}
