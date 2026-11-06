package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.editor.Document

// Working out, from the text alone, whether the caret sits
// directly inside a "sup Name ext SuperCls { ... }" block, and
// which of the superclass's methods that block could still
// override. Read from the text rather than the parse tree, as
// a half-typed member breaks the parse of the whole block.
object SppOverrideLookup {

  // A "sup ext" block the caret is directly inside: where its
  // "{" is, and the two types as written in its header.
  data class Block(val open: Int, val name: String, val superclass: String)

  // The header of a "sup ext" block, up to its "{": optional
  // annotations, "sup", optional generics, the type, "ext", and
  // the superclass.
  private val HEADER = Regex("""^\s*(?:!\S+\s+)*sup\s*(?:\[[^\]]*]\s*)?(.+?)\s+ext\s+(.+?)\s*$""")

  // The block whose members the caret is between, or null when
  // the innermost open "{" before the caret is not a "sup ext"
  // block's (a method body, a class, or nothing at all).
  fun enclosingBlock(text: CharSequence, offset: Int): Block? {
    val open = innermostOpenBrace(text, offset) ?: return null
    val lineStart = text.lastIndexOf('\n', open - 1) + 1
    val match = HEADER.find(text.subSequence(lineStart, open)) ?: return null
    return Block(open, match.groupValues[1], match.groupValues[2])
  }

  // What [block] could still override, from the last analysis
  // of the file: the entry whose types are written the same,
  // nearest the block if there are several, without the methods
  // the block's text already holds (written since that analysis).
  // Null when no entry matches: the block is newer than it.
  fun pending(
    lists: List<SppOverridableList>,
    block: Block,
    text: CharSequence,
    document: Document,
  ): List<SppOverridable>? {
    val line = document.getLineNumber(block.open)
    val list = lists
      .filter { squash(it.name) == squash(block.name) && squash(it.superclass) == squash(block.superclass) }
      .minByOrNull { kotlin.math.abs(it.where.startLine - line) }
      ?: return null
    val body = squash(blockBody(text, block.open))
    return list.methods.filter { squash(it.prototype) !in body }
  }

  // The text between a block's "{" and its matching "}", or the
  // end of the file when it has not been closed yet.
  fun blockBody(text: CharSequence, open: Int): CharSequence {
    var depth = 0
    var i = open
    while (i < text.length) {
      i = skipNonCode(text, i)
      if (i >= text.length) break
      when (text[i]) {
        '{' -> depth++
        '}' -> if (--depth == 0) return text.subSequence(open + 1, i)
      }
      i++
    }
    return text.subSequence((open + 1).coerceAtMost(text.length), text.length)
  }

  // The "{" of the innermost block still open at [offset], not
  // counting braces in comments, strings or char literals.
  fun innermostOpenBrace(text: CharSequence, offset: Int): Int? {
    val open = ArrayDeque<Int>()
    var i = 0
    val end = offset.coerceAtMost(text.length)
    while (i < end) {
      i = skipNonCode(text, i)
      if (i >= end) break
      when (text[i]) {
        '{' -> open.addLast(i)
        '}' -> open.removeLastOrNull()
      }
      i++
    }
    return open.lastOrNull()
  }

  // From [i], past a comment ("#" to the end of the line), a
  // string or a char literal starting there; [i] itself when
  // none does.
  private fun skipNonCode(text: CharSequence, start: Int): Int {
    var i = start
    while (i < text.length) {
      when (text[i]) {
        '#' -> {
          while (i < text.length && text[i] != '\n') i++
        }
        '"', '\'' -> {
          val quote = text[i++]
          while (i < text.length && text[i] != quote && text[i] != '\n') {
            if (text[i] == '\\') i++
            i++
          }
          i++
        }
        else -> return i
      }
    }
    return i
  }

  // Text with its whitespace removed, so a header compares equal
  // however it was spaced.
  private fun squash(text: CharSequence): String = text.filterNot { it.isWhitespace() }.toString()
}
