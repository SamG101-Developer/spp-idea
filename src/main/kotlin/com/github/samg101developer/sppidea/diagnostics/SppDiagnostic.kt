package com.github.samg101developer.sppidea.diagnostics

// One place an error points at, containing information about
// error location, text labels, and whether it's context or the
// actual error line. All encoding/position info is UTF-16.
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

/// A span location, containing the file to span in, the start
// and end lines and columns, and whether it is generated code
// or not. All encoding/position info is UTF-16.
data class SppSpan(
  val file: String,
  val startLine: Int,
  val startCharacter: Int,
  val endLine: Int,
  val endCharacter: Int,
  val generated: Boolean,
)

// An abstracted symbol, containing the relevant information
// from the compiler's output. This is what hover reads, and
// what go-to-definition jumps by.
data class SppSymbol(
  val kind: String,
  val name: String,
  val type: String,
  val use: SppSpan,
  val definition: SppSpan,
  val value: String = "", // Compile-time constant's evaluated value (empty for non-constants)
)

// The label's place, in the shape the rest of the plugin
// measures positions in.
fun SppLabel.asSpan(): SppSpan = SppSpan(file, startLine, startCharacter, endLine, endCharacter, generated)

// One thing reachable through a "." or a "::": a method,
// an attribute, a constant, or a namespace under another.
data class SppMember(
  val name: String,
  val kind: String,
  val type: String,
  val definition: SppSpan?,
  val signatures: List<String> = emptyList(),
  val visibility: String = "",
)

// Everything one type or namespace holds, which is what
// a completion list after "." or "::" offers.
data class SppMemberList(
  val owner: String,
  val of: String,
  val members: List<SppMember>,
)

// What a call resolved to: where its arguments are written,
// and the parameters they fill.
data class SppSignature(
  val name: String,
  val arguments: SppSpan,
  val params: List<SppMember>,
)

// What a "cmp" declaration computed, which the compiler
// knows because it ran it.
data class SppComptimeValue(
  val name: String,
  val value: String,
  val where: SppSpan,
)

// What one part of a file can name: everything the scope
// covering that region holds.
data class SppScope(
  val where: SppSpan,
  val names: List<SppMember>,
)

// One error, as `spp build --message-format=json` reports
// it.
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


// Reads the compiler's diagnostics out of a run's output,
// which is one JSON object per line.
object SppDiagnosticParser {

  // From compiler JSON output, create the list of diagnostics
  // that will be shown in the editor. Each diagnostic is one
  // error, with its code, title, severity, labels, note and
  // help text.
  fun parse(output: String): List<SppDiagnostic> =
    objectsIn(output, "diagnostic").mapNotNull { toDiagnostic(it) }

  // From compiler JSON output, create the list of symbols that
  // will be scanned.
  fun parseSymbols(output: String): List<SppSymbol> =
    objectsIn(output, "symbol").mapNotNull { toSymbol(it) }

  // From compiler JSON output, create the list of signatures
  // that will be scanned. Each signature is the way a function
  // is called, and the parameters it takes. A function with
  // several overloads has several signatures, one per overload.
  fun parseSignatures(output: String): List<SppSignature> =
    objectsIn(output, "signature").mapNotNull { obj ->
      SppSignature(
        name = obj["name"] as? String ?: "",
        arguments = toSpan(obj["arguments"]) ?: return@mapNotNull null,
        params = membersOf(obj),
      )
    }

  // From compiler JSON output, create the list of compile-time
  // declarations and their evaluated values.
  fun parseComptimeValues(output: String): List<SppComptimeValue> =
    objectsIn(output, "comptime").mapNotNull { obj ->
      SppComptimeValue(
        name = obj["name"] as? String ?: "",
        value = obj["value"] as? String ?: return@mapNotNull null,
        where = toSpan(obj["where"]) ?: return@mapNotNull null,
      )
    }

  // From compiler JSON output, create the list of scopes that
  // will be scanned. Each scope is a region of a file, and the
  // names that are reachable in that region.
  fun parseScopes(output: String): List<SppScope> = objectsIn(output, "scope").mapNotNull { obj ->
    SppScope(where = toSpan(obj["where"]) ?: return@mapNotNull null, names = membersOf(obj))
  }

  // From compiler JSON output, create the list of members that
  // will be scanned. The "members" are the things reachable
  // through "." or "::" from a type or namespace, and the "of"
  // is the type or namespace they are in.
  fun parseMembers(output: String): List<SppMemberList> =
    objectsIn(output, "members").mapNotNull { obj ->
      SppMemberList(
        owner = obj["owner"] as? String ?: return@mapNotNull null,
        of = obj["of"] as? String ?: "",
        members = membersOf(obj),
      )
    }

  // From a compiler JSON object, mapping an object's "members"
  // field to a list of [SppMember]s.
  private fun membersOf(obj: Map<*, *>): List<SppMember> =
    (obj["members"] as? List<*>).orEmpty().mapNotNull { toMember(it) }

  // Extract JSON objects from the compiler's output, which
  // is one per line, and filter by the "kind" field. Any line
  // that is not a JSON object is ignored (progress bars, etc).
  private fun objectsIn(output: String, kind: String): List<Map<*, *>> =
    output.split('\r', '\n')
      .map { it.trim() }
      .filter { it.startsWith("{") && it.endsWith("}") }
      .mapNotNull { runCatching { JsonReader(it).readValue() as? Map<*, *> }.getOrNull() }
      .filter { it["kind"] == kind }

  // Convert a compiler JSON object to an [SppMember], which is
  // one of the things reachable through "." or "::" from a type
  // or namespace.
  private fun toMember(value: Any?): SppMember? {
    val obj = value as? Map<*, *> ?: return null
    return SppMember(
      name = obj["name"] as? String ?: return null,
      kind = obj["member"] as? String ?: "",
      type = obj["type"] as? String ?: "",
      definition = toSpan(obj["definition"]),
      signatures = (obj["signatures"] as? List<*>).orEmpty().filterIsInstance<String>(),
      visibility = obj["visibility"] as? String ?: "",
    )
  }

  // Convert a compiler JSON object to an [SppSymbol], which is
  // one of the things the compiler knows about a name in a file.
  private fun toSymbol(obj: Map<*, *>): SppSymbol? = SppSymbol(
    kind = obj["symbol"] as? String ?: return null,
    name = obj["name"] as? String ?: "",
    type = obj["type"] as? String ?: "",
    use = toSpan(obj["use"]) ?: return null,
    definition = toSpan(obj["definition"]) ?: return null,
  )

  // Convert a compiler JSON object to an [SppSpan], which is a
  // location in a file, with start and end lines and columns.
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

  // Convert a compiler JSON object to an [SppDiagnostic], which is
  // one of the errors the compiler reported, with its code, title,
  // severity, labels, note and help text.
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

  // Convert a compiler JSON object to an [SppLabel], which
  // is one of the places an error points at, with its file,
  // message, primary flag, start and end lines and columns,
  // and generated flag.
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

// A minimal JSON reader, supporting: objects, arrays, strings,
// numbers, booleans and null. This is all the compiler emits.
private class JsonReader(private val text: String) {

  private var at = 0

  // Generic "read a value" function, which dispatches to the
  // right reader based on the first character. This is the
  // entry point for reading a JSON value, and is called
  // recursively for nested objects and arrays.
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

  // Read a JSON object, which is a set of key-value pairs
  // enclosed in braces. The keys are strings, and the values
  // are any JSON value. The pairs are separated by commas.
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

  // Read a JSON array, which is a list of values enclosed in
  // brackets. The values are separated by commas.
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

  // Read a JSON string, which is a sequence of characters
  // enclosed in double quotes. The string may contain escape
  // sequences, which are interpreted according to the JSON
  // specification.
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

  // Read a JSON escape sequence, which is a backslash followed
  // by a character that indicates the type of escape. The
  // escape sequence is interpreted according to the JSON
  // specification, and the resulting character is returned.
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

  // Read a JSON number, which is a sequence of digits that may
  // include a decimal point and an exponent. The number is
  // interpreted according to the JSON specification, and the
  // resulting value is returned as a Double.
  private fun readNumber(): Double {
    val start = at
    while (
      at < text.length && (text[at] == '-' || text[at] == '+' || text[at] == '.' || text[at] == 'e' || text[at] == 'E' || text[at].isDigit())
    ) {
      at++
    }
    return text.substring(start, at).toDouble()
  }

  // Read a JSON literal, which is a fixed string that represents
  // a specific value. The literal is compared to the expected
  // string, and if it matches, the corresponding value is
  // returned. If it does not match, an error is thrown.
  private fun <T> readLiteral(literal: String, value: T): T {
    require(text.startsWith(literal, at)) { "expected '$literal' at $at" }
    at += literal.length
    return value
  }

  // Skip whitespace characters, which are ignored in JSON. This
  // function advances the current position until a non-whitespace
  // character is found, or the end of the input is reached.
  private fun skipSpace() {
    while (at < text.length && text[at].isWhitespace()) at++
  }

  // Peek at the next character without consuming it. If the end
  // of the input is reached, an error is thrown.
  private fun peek(): Char =
    if (at < text.length) text[at] else error("unexpected end of json")

  // Consume the next character and return it. If the end of the
  // input is reached, an error is thrown.
  private fun next(): Char =
    peek().also { at++ }

  // Expect the next character to be the given one, and consume
  // it. If it is not, throw an error.
  private fun expect(c: Char) {
    require(next() == c) { "expected '$c' at ${at - 1}" }
  }
}
