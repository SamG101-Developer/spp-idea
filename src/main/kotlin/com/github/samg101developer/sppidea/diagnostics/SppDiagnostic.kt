package com.github.samg101developer.sppidea.diagnostics

/**
 * One place an error points at: where it is, what it says about it, and whether it is the place the error is reported
 * (the primary label) or one that explains it - "first defined here", "moved here". The line and character are what
 * `spp` printed them as: zero-based, and counted in UTF-16 code units, which is what a [com.intellij.openapi.editor.Document]
 * counts offsets in too, so no conversion is needed on this side.
 */
data class SppLabel(
  val file: String,
  val message: String,
  val primary: Boolean,
  val startLine: Int,
  val startCharacter: Int,
  val endLine: Int,
  val endCharacter: Int,
  val generated: Boolean,
)

/** Where something is, as the compiler reports it: a file plus a zero-based line and UTF-16 column. */
data class SppSpan(
  val file: String,
  val startLine: Int,
  val startCharacter: Int,
  val endLine: Int,
  val endCharacter: Int,
  val generated: Boolean,
)

/**
 * One name in the source and what it resolved to: what kind of thing it is, what it is called, the type it has, and
 * where it was declared. This is what hover reads, and what go-to-definition jumps by.
 */
data class SppSymbol(
  val kind: String,
  val name: String,
  val type: String,
  val use: SppSpan,
  val definition: SppSpan,
  /** What a compile-time constant worked out to; empty for everything that is not one. */
  val value: String = "",
)

/** The label's place, in the shape the rest of the plugin measures positions in. */
fun SppLabel.asSpan(): SppSpan = SppSpan(file, startLine, startCharacter, endLine, endCharacter, generated)

/** One thing reachable through a "." or a "::": a method, an attribute, a constant, or a namespace under another. */
data class SppMember(
  val name: String,
  val kind: String,
  val type: String,
  val definition: SppSpan?,
  /** How a function can be called, one per overload; empty for everything that is not one. */
  val signatures: List<String> = emptyList(),
)

/** Everything one type or namespace holds, which is what a completion list after "." or "::" offers. */
data class SppMemberList(
  val owner: String,
  val of: String,
  val members: List<SppMember>,
)

/** What a call resolved to: where its arguments are written, and the parameters they fill. */
data class SppSignature(
  val name: String,
  val arguments: SppSpan,
  val params: List<SppMember>,
)

/** What a "cmp" declaration computed, which the compiler knows because it ran it. */
data class SppComptimeValue(
  val name: String,
  val value: String,
  val where: SppSpan,
)

/** What one part of a file can name: everything the scope covering that region holds. */
data class SppScope(
  val where: SppSpan,
  val names: List<SppMember>,
)

/** One error, as `spp build --message-format=json` reports it. */
data class SppDiagnostic(
  val code: String,
  val title: String,
  val severity: String,
  val labels: List<SppLabel>,
  val note: String,
  val help: String,
) {
  val primaryLabel: SppLabel? get() = labels.firstOrNull { it.primary }
}

/**
 * Reads the compiler's diagnostics out of a run's output.
 *
 * The parser below is deliberately small and self-contained rather than a library call: the only json this plugin
 * ever reads is what the compiler itself writes, one object per line, and a bundled json library is not something a
 * plugin can count on being there at runtime.
 */
object SppDiagnosticParser {

  /** Every diagnostic in [output]; anything that is not a json object (progress bars, notices) is skipped. */
  fun parse(output: String): List<SppDiagnostic> = objectsIn(output, "diagnostic").mapNotNull { toDiagnostic(it) }

  /** Every resolved name in [output], which the compiler reports for the one file it was asked to index. */
  fun parseSymbols(output: String): List<SppSymbol> = objectsIn(output, "symbol").mapNotNull { toSymbol(it) }

  /** Every call in the indexed files, with the parameters it resolved to. */
  fun parseSignatures(output: String): List<SppSignature> = objectsIn(output, "signature").mapNotNull { obj ->
    SppSignature(
      name = obj["name"] as? String ?: "",
      arguments = toSpan(obj["arguments"]) ?: return@mapNotNull null,
      params = membersOf(obj),
    )
  }

  /** What each "cmp" declaration in the indexed files computed. */
  fun parseComptimeValues(output: String): List<SppComptimeValue> =
    objectsIn(output, "comptime").mapNotNull { obj ->
      SppComptimeValue(
        name = obj["name"] as? String ?: "",
        value = obj["value"] as? String ?: return@mapNotNull null,
        where = toSpan(obj["where"]) ?: return@mapNotNull null,
      )
    }

  /** What each part of the indexed files can name. */
  fun parseScopes(output: String): List<SppScope> = objectsIn(output, "scope").mapNotNull { obj ->
    SppScope(where = toSpan(obj["where"]) ?: return@mapNotNull null, names = membersOf(obj))
  }

  private fun membersOf(obj: Map<*, *>): List<SppMember> =
    (obj["members"] as? List<*>).orEmpty().mapNotNull { toMember(it) }

  /** What each type and namespace reached from the indexed files holds. */
  fun parseMembers(output: String): List<SppMemberList> = objectsIn(output, "members").mapNotNull { obj ->
    SppMemberList(
      owner = obj["owner"] as? String ?: return@mapNotNull null,
      of = obj["of"] as? String ?: "",
      members = membersOf(obj),
    )
  }

  private fun toMember(value: Any?): SppMember? {
    val obj = value as? Map<*, *> ?: return null
    return SppMember(
      name = obj["name"] as? String ?: return null,
      kind = obj["member"] as? String ?: "",
      type = obj["type"] as? String ?: "",
      definition = toSpan(obj["definition"]),
      signatures = (obj["signatures"] as? List<*>).orEmpty().filterIsInstance<String>(),
    )
  }

  /** The json objects in [output] of one kind, skipping progress bars, notices and anything unparseable. */
  private fun objectsIn(output: String, kind: String): List<Map<*, *>> =
    output.split('\r', '\n')
      .map { it.trim() }
      .filter { it.startsWith("{") && it.endsWith("}") }
      .mapNotNull { runCatching { JsonReader(it).readValue() as? Map<*, *> }.getOrNull() }
      .filter { it["kind"] == kind }

  private fun toSymbol(obj: Map<*, *>): SppSymbol? = SppSymbol(
    kind = obj["symbol"] as? String ?: return null,
    name = obj["name"] as? String ?: "",
    type = obj["type"] as? String ?: "",
    use = toSpan(obj["use"]) ?: return null,
    definition = toSpan(obj["definition"]) ?: return null,
  )

  private fun toSpan(value: Any?): SppSpan? {
    val obj = value as? Map<*, *> ?: return null
    val start = obj["start"] as? Map<*, *>
    val end = obj["end"] as? Map<*, *>
    return SppSpan(
      file = obj["file"] as? String ?: return null,
      startLine = (start?.get("line") as? Double)?.toInt() ?: 0,
      startCharacter = (start?.get("character") as? Double)?.toInt() ?: 0,
      endLine = (end?.get("line") as? Double)?.toInt() ?: 0,
      endCharacter = (end?.get("character") as? Double)?.toInt() ?: 0,
      generated = obj["generated"] == true || start == null || end == null,
    )
  }

  private fun toDiagnostic(obj: Map<*, *>): SppDiagnostic? {
    val labels = (obj["labels"] as? List<*>).orEmpty().mapNotNull { toLabel(it) }
    return SppDiagnostic(
      code = obj["code"] as? String ?: "",
      title = obj["title"] as? String ?: "",
      severity = obj["severity"] as? String ?: "error",
      labels = labels,
      note = obj["note"] as? String ?: "",
      help = obj["help"] as? String ?: "",
    )
  }

  private fun toLabel(value: Any?): SppLabel? {
    val obj = value as? Map<*, *> ?: return null
    val start = obj["start"] as? Map<*, *>
    val end = obj["end"] as? Map<*, *>
    val generated = obj["generated"] == true || start == null || end == null
    return SppLabel(
      file = obj["file"] as? String ?: return null,
      message = obj["message"] as? String ?: "",
      primary = obj["primary"] == true,
      startLine = (start?.get("line") as? Double)?.toInt() ?: 0,
      startCharacter = (start?.get("character") as? Double)?.toInt() ?: 0,
      endLine = (end?.get("line") as? Double)?.toInt() ?: 0,
      endCharacter = (end?.get("character") as? Double)?.toInt() ?: 0,
      generated = generated,
    )
  }
}

/**
 * A minimal json reader: objects, arrays, strings, numbers, booleans and null, which is all the compiler emits.
 * Numbers come back as [Double], the way every json parser without a schema hands them over.
 */
private class JsonReader(private val text: String) {

  private var at = 0

  fun readValue(): Any? {
    skipSpace()
    return when (val c = peek()) {
      '{' -> readObject()
      '[' -> readArray()
      '"' -> readString()
      't' -> readLiteral("true", true)
      'f' -> readLiteral("false", false)
      'n' -> readLiteral("null", null)
      else -> if (c == '-' || c.isDigit()) readNumber() else error("unexpected '$c' at $at")
    }
  }

  private fun readObject(): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    expect('{')
    skipSpace()
    if (peek() == '}') {
      at++; return out
    }
    while (true) {
      skipSpace()
      val key = readString()
      skipSpace()
      expect(':')
      out[key] = readValue()
      skipSpace()
      when (val c = next()) {
        ',' -> continue
        '}' -> return out
        else -> error("expected ',' or '}' but found '$c' at ${at - 1}")
      }
    }
  }

  private fun readArray(): List<Any?> {
    val out = ArrayList<Any?>()
    expect('[')
    skipSpace()
    if (peek() == ']') {
      at++; return out
    }
    while (true) {
      out += readValue()
      skipSpace()
      when (val c = next()) {
        ',' -> continue
        ']' -> return out
        else -> error("expected ',' or ']' but found '$c' at ${at - 1}")
      }
    }
  }

  private fun readString(): String {
    expect('"')
    val out = StringBuilder()
    while (true) {
      when (val c = next()) {
        '"' -> return out.toString()
        '\\' -> out.append(readEscape())
        else -> out.append(c)
      }
    }
  }

  private fun readEscape(): Char = when (val c = next()) {
    '"', '\\', '/' -> c
    'b' -> '\b'
    'f' -> '\u000C'
    'n' -> '\n'
    'r' -> '\r'
    't' -> '\t'
    'u' -> text.substring(at, at + 4).toInt(16).toChar().also { at += 4 }
    else -> error("unknown escape '\\$c' at ${at - 1}")
  }

  private fun readNumber(): Double {
    val start = at
    while (at < text.length && (text[at] == '-' || text[at] == '+' || text[at] == '.' ||
          text[at] == 'e' || text[at] == 'E' || text[at].isDigit())
    ) {
      at++
    }
    return text.substring(start, at).toDouble()
  }

  private fun <T> readLiteral(literal: String, value: T): T {
    require(text.startsWith(literal, at)) { "expected '$literal' at $at" }
    at += literal.length
    return value
  }

  private fun skipSpace() {
    while (at < text.length && text[at].isWhitespace()) at++
  }

  private fun peek(): Char = if (at < text.length) text[at] else error("unexpected end of json")

  private fun next(): Char = peek().also { at++ }

  private fun expect(c: Char) {
    require(next() == c) { "expected '$c' at ${at - 1}" }
  }
}
