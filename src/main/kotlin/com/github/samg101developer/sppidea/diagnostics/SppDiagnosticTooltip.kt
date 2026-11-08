package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI

// Renders a compiler error as the HTML shown when hovering
// one of its labels. The layout, top to bottom:
//
//   Type Mismatch Error  E1          (title, code greyed)
//   Found type Std::Str              (what the hovered label says,
//                                     `quoted` spans shown as code)
//   Related:                         (every other place, linked)
//     main.spp:4  Expected type S32
//   Note: ...                        (why)
//   Help: ...                        (how to fix it)
//   Wrapped error:                   (the caught error, verbatim)
//
object SppDiagnosticTooltip {

  // The tooltip for [diagnostic] when hovering [current], which
  // lives in [hereFile]. [offsetOf] gives a label's character
  // offset in its file, for the link that jumps to it, or null
  // when it can't be found (then the location isn't a link).
  fun render(
    diagnostic: SppDiagnostic,
    current: SppLabel,
    hereFile: String,
    offsetOf: (SppLabel) -> Int?,
  ): String {
    val html = HtmlBuilder()

    // Header: the title in bold, the code beside it greyed.
    html.append(HtmlChunk.text(diagnostic.title.ifBlank { "Error" }).bold())
    if (diagnostic.code.isNotBlank()) {
      html.nbsp(2).append(HtmlChunk.text(diagnostic.code).wrapWith(HtmlChunk.span("color: ${greyHex()}")))
    }

    // What this place says, as the main line.
    if (current.message.isNotBlank()) {
      html.append(HtmlChunk.div().child(withCode(current.message)).style("margin-top: 4px"))
    }

    // Every other place the error points at: the error itself
    // (in bold) when hovering an explanation, and the explanations.
    val others = diagnostic.labels.filter { it !== current && !it.generated }
    if (others.isNotEmpty()) {
      val rows = HtmlBuilder()
      for (label in others) {
        val row = HtmlBuilder()
          .append(location(label, hereFile, offsetOf))
          .nbsp(2)
          .append(if (label.primary) withCode(label.message).wrapWith("b") else withCode(label.message))
        rows.append(HtmlChunk.div().child(row.toFragment()))
      }
      html.append(section("Related:", rows.toFragment(), block = true))
    }

    // The footer: why the code is wrong, then how to fix it.
    if (diagnostic.note.isNotBlank()) html.append(section("Note:", withCode(oneParagraph(diagnostic.note))))
    if (diagnostic.help.isNotBlank()) html.append(section("Help:", withCode(oneParagraph(diagnostic.help))))

    // A caught error (e.g. the real error in generated code),
    // which is the compiler's own terminal layout, kept as-is
    // (raw, as text() would turn its newlines into <br/>).
    for (wrapped in diagnostic.wrapped) {
      html.append(section("Wrapped error:", HtmlChunk.raw(StringUtil.escapeXmlEntities(wrapped.trimEnd())).wrapWith("pre"), block = true))
    }

    return html.wrapWithHtmlBody().toString()
  }

  // The short message for the Problems view and the status bar:
  // "E1 Type Mismatch Error: Found type Std::Str".
  fun description(diagnostic: SppDiagnostic, label: SppLabel): String {
    val head = listOf(diagnostic.code, diagnostic.title).filter { it.isNotBlank() }.joinToString(" ")
    return when {
      head.isEmpty() -> label.message
      label.message.isBlank() -> head
      else -> "$head: ${label.message}"
    }
  }

  // "line 4" for a place in the hovered file, "other.spp:4"
  // for one elsewhere; a link when it can be jumped to.
  private fun location(label: SppLabel, hereFile: String, offsetOf: (SppLabel) -> Int?): HtmlChunk {
    val line = label.startLine + 1
    val text = if (label.file == hereFile) "line $line" else "${label.file.substringAfterLast('/').substringAfterLast('\\')}:$line"
    val offset = offsetOf(label) ?: return HtmlChunk.text(text)
    return HtmlChunk.link("#navigation/${label.file}:$offset", text)
  }

  // A labelled part of the tooltip: the label in bold, then
  // the body on the same line, or under it when [block].
  private fun section(label: String, body: HtmlChunk, block: Boolean = false): HtmlChunk {
    val inner = HtmlBuilder().append(HtmlChunk.text(label).bold())
    if (block) inner.append(HtmlChunk.div().child(body).style("margin-left: 8px"))
    else inner.nbsp().append(body)
    return HtmlChunk.div().child(inner.toFragment()).style("margin-top: 6px")
  }

  // Compiler text with each `backticked` span (a type, a name,
  // a value the compiler highlighted) shown as code. A trailing
  // unpaired backtick is kept as a plain character.
  internal fun withCode(text: String): HtmlChunk {
    val parts = text.split('`')
    val html = HtmlBuilder()
    parts.forEachIndexed { i, part ->
      val isCode = i % 2 == 1
      when {
        isCode && i == parts.lastIndex -> html.append("`$part")
        isCode -> html.append(HtmlChunk.text(part).wrapWith("code"))
        part.isNotEmpty() -> html.append(part)
      }
    }
    return html.toFragment()
  }

  // The compiler hard-wraps its notes for a terminal; the
  // tooltip wraps by itself, so the breaks become spaces.
  private fun oneParagraph(text: String): String =
    text.lines().joinToString(" ") { it.trim() }.trim()

  // The theme's colour for secondary text, as "#rrggbb".
  private fun greyHex(): String =
    "#" + ColorUtil.toHex(JBUI.CurrentTheme.ContextHelp.FOREGROUND)
}
