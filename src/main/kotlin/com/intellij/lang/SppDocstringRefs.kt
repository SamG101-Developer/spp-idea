package com.intellij.lang

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace

// A parameter reference in a docstring: "[name]", naming one of
// the parameters the docstring documents (on a class, one of its
// attributes - whatever "@let" documents there). It is a bare
// name in square brackets: a "[" straight after a name ("Vec[T]")
// opens generic arguments, and one followed by "(" is a link, so
// neither is a reference. Inline code ("`[x]`") and code blocks
// are left alone. Shared by the highlighting, the validation, the
// wrapping and the rendered documentation, so all four agree.
object SppDocstringRefs {
  // One reference: the name, where "[name]" sits in the text it
  // was found in, and where the name itself sits.
  data class Ref(val name: String, val range: IntRange, val nameRange: IntRange)

  private val refPattern = Regex("""\[([A-Za-z_][A-Za-z0-9_]*)](?!\()""")
  private val inlineCodePattern = Regex("""`[^`\n]+`""")

  // Every reference in a single line of docstring text, outside
  // its inline code.
  fun findIn(text: String): List<Ref> {
    val code = inlineCodePattern.findAll(text).map { it.range }.toList()
    return refPattern.findAll(text)
      .filter { isStandalone(text, it.range.first) }
      .filter { m -> code.none { m.range.first >= it.first && m.range.last <= it.last } }
      .map { Ref(it.groupValues[1], it.range, it.groups[1]!!.range) }
      .toList()
  }

  // The reference starting exactly at [index], if there is one.
  // For a renderer walking the text a character at a time, which
  // has already consumed the inline code before reaching here.
  fun refAt(text: String, index: Int): Ref? {
    if (index >= text.length || text[index] != '[' || !isStandalone(text, index)) return null
    val match = refPattern.matchAt(text, index) ?: return null
    return Ref(match.groupValues[1], match.range, match.groups[1]!!.range)
  }

  // Whether a docstring line sits inside a ``` code block, from
  // the docstring lines above it: an odd number of fences above
  // means one is open.
  fun isInCodeFence(comment: PsiComment): Boolean {
    var fences = 0
    var sibling = comment.prevSibling
    while (sibling != null) {
      when (sibling) {
        is PsiComment -> if (isFence(sibling.text)) fences++
        is PsiWhiteSpace -> {}
        else -> break
      }
      sibling = sibling.prevSibling
    }
    return fences % 2 == 1
  }

  // Whether a docstring line (with its "#") opens or closes a code
  // block.
  fun isFence(commentText: String): Boolean = commentText.removePrefix("#").trimStart().startsWith("```")

  // A "[" directly after a name or a closing bracket is an index
  // or a generic argument list, not a reference.
  private fun isStandalone(text: String, open: Int): Boolean {
    if (open == 0) return true
    val before = text[open - 1]
    return !(before.isLetterOrDigit() || before == '_' || before == ']')
  }
}
