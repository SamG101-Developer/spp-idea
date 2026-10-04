package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.lang.SppSyntaxHighlighter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlin.io.path.absolute

// Writes the parts of an error that explain it at the end
// of the lines they point at. An error usually has additional
// context too, all of which is already carried on the
// diagnostic. The context and error labels are written next
// to the error line, with everything else in the function
// greyed out.
class SppErrorLinePainter : EditorLinePainter() {

  // The editor paints the lines while the analysis is running,
  // so only answer for what the last analysis already worked
  // out. The answer is a list of what the errors say about
  // this line, and any "cmp" values that were computed for it.
  override fun getLineExtensions(project: Project, file: VirtualFile, lineNumber: Int): Collection<LineExtensionInfo> {
    if (file.extension != "spp") return emptyList()
    val path = runCatching { file.toNioPath().absolute() }.getOrNull() ?: return emptyList()

    // Only what the last analysis already worked out: this
    // is asked while the editor paints.
    val service = SppCompilerDiagnostics.getInstance(project)
    val errors = explanationsOn(service.cachedDiagnosticsFor(path), path.toString(), lineNumber)
      .map {
        LineExtensionInfo(
          "    ${it.message}",
          attributesOf(if (it.primary) SppSyntaxHighlighter.INLINE_ERROR else SppSyntaxHighlighter.INLINE_EXPLANATION)
        )
      }

    // A "cmp" is computed while the program is compiled, so
    // the evaluated value is written next to the cmp line too.
    val values = service.cachedComptimeValuesFor(path)
      .filter { !it.where.generated && it.where.startLine == lineNumber }
      .map { LineExtensionInfo("    = ${it.value}", attributesOf(SppSyntaxHighlighter.INLINE_CMP_VALUE)) }

    return errors + values
  }

}

// The message being rendered on the line information, and a
// flag for is this is context information (primary=false), or
// the main error (primary=true). Context and error lines have
// different colours.
data class Explanation(val message: String, val primary: Boolean)

// The collection is sorted so that the main error is first,
// and the context lines follow. The main error is the one that
// is highlighted as-well and labelled.
fun explanationsOn(diagnostics: List<SppDiagnostic>, file: String, line: Int): List<Explanation> = diagnostics
  .asSequence()
  .flatMap { it.labels }
  .filter { !it.generated && it.startLine == line && it.file == file }
  .map { Explanation(it.message, it.primary) }
  .distinct()
  .sortedByDescending { it.primary }
  .toList()

/// The colours come from the scheme (the S++ colour settings
// page), so they can be changed there.
private fun attributesOf(key: TextAttributesKey): TextAttributes =
  EditorColorsManager.getInstance().globalScheme.getAttributes(key) ?: TextAttributes()
