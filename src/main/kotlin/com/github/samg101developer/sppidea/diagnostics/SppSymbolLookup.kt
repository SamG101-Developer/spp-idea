package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

// A utility class to provide information about symbols, their
// uses, and the surrounding text. It is used by the completion
// and navigation features.
object SppSymbolLookup {

  // The span as a range in [document], or null when it falls
  // outside what the editor holds.
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

  // The offset a span starts at in [document], or null when
  // it has no place there.
  fun offsetOf(span: SppSpan, document: Document): Int? {
    if (span.generated || span.startLine < 0 || span.startLine >= document.lineCount) return null
    val lineStart = document.getLineStartOffset(span.startLine)
    return (lineStart + span.startCharacter).coerceAtMost(document.getLineEndOffset(span.startLine))
  }

  // What is being competed at the offset. It gets the prefix
  // typed so far, and if it is after a "." or "::" it also
  // gets the end of what is being accessed.
  fun accessBefore(text: CharSequence, offset: Int): Access? {
    var at = offset.coerceIn(0, text.length)

    // The part of the name already typed, which is what the
    // offered list is filtered by.
    val prefixEnd = at
    while (at > 0 && (text[at - 1].isLetterOrDigit() || text[at - 1] == '_')) at--
    val prefix = text.subSequence(at, prefixEnd).toString()

    // The operator that is being used to access something,
    // worked out by the following tokens.
    while (at > 0 && text[at - 1].isWhitespace()) at--
    val operator = when {
      at >= 2 && text[at - 1] == ':' && text[at - 2] == ':' -> "::"
      at >= 1 && text[at - 1] == '.' -> "."
      else -> return null
    }

    // The end of what is being accessed, which is the end of the
    // name or expression before the operator, so that the
    // completion can find what is being accessed and offer its
    // members.
    at -= operator.length
    while (at > 0 && text[at - 1].isWhitespace()) at--
    return Access(operator, receiverEnd = at, prefix = prefix)
  }

  // The receiver is the symbol whose use ends at the access's
  // receiverEnd, and the widest of those is taken. For example,
  // in "std::mem::ops::dr|", the receiver is the "ops" symbol,
  // not "mem" or "std".
  fun receiverOf(symbols: List<SppSymbol>, access: Access, document: Document): SppSymbol? = symbols
    .mapNotNull { symbol -> rangeOf(symbol.use, document)?.let { symbol to it } }
    .filter { (_, range) -> range.endOffset == access.receiverEnd }
    .maxByOrNull { (_, range) -> range.length }
    ?.first

  // The "short type name" is teh final part of the qualified
  // type, but keeps the borrow convention, generics, etc.
  fun shortTypeName(type: String): String =
    QUALIFIED_NAME.replace(type) { it.value.substringAfterLast("::") }

  private val QUALIFIED_NAME = Regex("""[A-Za-z_][A-Za-z0-9_]*(?:::[A-Za-z_][A-Za-z0-9_]*)+""")

  // Use regex to filter out the "self" parameter, and check
  // if there are any other parameters left over. If so, this
  // function has parameters that can take written values.
  fun callParameters(signature: String): List<String> {
    val inside = signature.substringBeforeLast(") ->").removePrefix("(")
    return inside.split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .filterIndexed { i, param -> i > 0 || !RECEIVER.matches(param) }
  }

  // Whether a call to this signature is written with anything
  // between its brackets. Handles the existence of the "self"
  // parameter which is implicitly passed.
  fun callTakesArguments(signature: String): Boolean =
    callParameters(signature).isNotEmpty()

  private val RECEIVER = Regex("""&?(?:mut )?self""")

  /** The part of a name typed before [offset], which is what an offered list is filtered by. */
  fun prefixBefore(text: CharSequence, offset: Int): String {
    var at = offset.coerceIn(0, text.length)
    val end = at
    while (at > 0 && (text[at - 1].isLetterOrDigit() || text[at - 1] == '_')) at--
    return text.subSequence(at, end).toString()
  }

  // Read backwards, to find the parenthesis that the caret is
  // inside, if any. It handles nested parentheses, so that in
  // "f(g(x), " the caret is inside the "(" of "f" rather than
  // the one of "g".
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

  // Where the thing before an open bracket ends: the name
  // of what is being called or built.
  fun calleeEnd(text: CharSequence, openBracket: Int): Int {
    var at = openBracket
    while (at > 0 && text[at - 1].isWhitespace()) at--
    return at
  }

  // Everything nameable at [offset]: what every scope
  // covering it holds, innermost last.
  fun namesInScopeAt(scopes: List<SppScope>, offset: Int, document: Document): List<SppMember> = scopes
    .mapNotNull { scope -> regionOf(scope.where, document)?.let { scope to it } }
    .filter { (_, region) -> offset >= region.first && offset <= region.second }
    .sortedByDescending { (_, region) -> region.second - region.first }
    .flatMap { (scope, _) -> scope.names }

  // The call whose arguments [offset] sits in, innermost
  // first.
  fun signatureAt(signatures: List<SppSignature>, offset: Int, document: Document): SppSignature? = signatures
    .mapNotNull { signature -> regionOf(signature.arguments, document)?.let { signature to it } }
    .filter { (_, region) -> offset >= region.first && offset <= region.second }
    .minByOrNull { (_, region) -> region.second - region.first }
    ?.first

  // A region that may cross lines, as a pair of offsets;
  // null when it falls outside the document.
  private fun regionOf(span: SppSpan, document: Document): Pair<Int, Int>? {
    if (span.generated || span.startLine < 0 || span.startLine >= document.lineCount) return null
    if (span.endLine < span.startLine || span.endLine >= document.lineCount) return null
    val start = (document.getLineStartOffset(span.startLine) + span.startCharacter)
      .coerceAtMost(document.getLineEndOffset(span.startLine))
    val end = (document.getLineStartOffset(span.endLine) + span.endCharacter)
      .coerceAtMost(document.getLineEndOffset(span.endLine))
    return if (end >= start) start to end else null
  }

  // An access written before a caret: which operator, where
  // what it is applied to ends, and what has been typed.
  data class Access(val operator: String, val receiverEnd: Int, val prefix: String)

  fun symbolAt(symbols: List<SppSymbol>, offset: Int, document: Document): SppSymbol? =
    symbols
      .mapNotNull { symbol -> rangeOf(symbol.use, document)?.let { symbol to it } }
      .filter { (_, range) -> offset >= range.startOffset && offset <= range.endOffset }
      .minByOrNull { (_, range) -> range.length }
      ?.first
}
