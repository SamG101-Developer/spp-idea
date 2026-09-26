package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import java.awt.Font
import kotlin.io.path.absolute

/**
 * Writes the parts of an error that explain it at the end of the lines they point at.
 *
 * An error usually has more to say than the one place it is reported: where a value was moved, where a name was first
 * defined, which block it escaped. All of that is already carried on the diagnostic, and putting it in a tooltip means
 * it is only read by someone who already suspects it. Written down the right-hand side, it is read while the error is
 * being looked at.
 */
class SppErrorLinePainter : EditorLinePainter() {

    override fun getLineExtensions(project: Project, file: VirtualFile, lineNumber: Int): Collection<LineExtensionInfo> {
        if (file.extension != "spp") return emptyList()
        val path = runCatching { file.toNioPath().absolute() }.getOrNull() ?: return emptyList()

        // Only what the last analysis already worked out: this is asked while the editor paints.
        val service = SppCompilerDiagnostics.getInstance(project)

        val errors = explanationsOn(service.cachedDiagnosticsFor(path), path.toString(), lineNumber)
            .map { LineExtensionInfo("    ${it.message}", if (it.primary) ERROR else EXPLANATION) }

        // A "cmp" is computed while the program is compiled, so the answer exists and is worth reading where it was
        // asked for - the whole point of writing one is the value, and the expression is only how it was reached.
        val values = service.cachedComptimeValuesFor(path)
            .filter { !it.where.generated && it.where.startLine == lineNumber }
            .map { LineExtensionInfo("    = ${it.value}", VALUE) }

        return errors + values
    }

    companion object {
        /**
         * What the errors in [diagnostics] have to say about [line] of [file]: what is wrong, where it is wrong, and
         * the places that explain it - where a value was moved, where a name was first defined. A squiggle says that
         * something is wrong there but not what; the reading is what this is for.
         */
        fun explanationsOn(diagnostics: List<SppDiagnostic>, file: String, line: Int): List<Explanation> = diagnostics
            .flatMap { it.labels }
            .filter { !it.generated && it.startLine == line && it.file == file }
            .map { Explanation(it.message, it.primary) }
            .distinct()
            .sortedByDescending { it.primary }

        /// Italic, so it reads as something written about the code rather than as code. What is wrong is in the
        /// colour of an error; what merely explains it is quieter, so the two are told apart at a glance.
        private val ERROR = TextAttributes().apply {
            foregroundColor = JBColor(0xC7222A, 0xCF6A6A)
            fontType = Font.ITALIC
        }

        /// Quieter than an error and a different colour again: it is an answer, not a complaint.
        private val VALUE = TextAttributes().apply {
            foregroundColor = JBColor(0x3A7A55, 0x5E9C78)
            fontType = Font.ITALIC
        }

        private val EXPLANATION = TextAttributes().apply {
            foregroundColor = JBColor(0x9A7B4F, 0xA08B62)
            fontType = Font.ITALIC
        }
    }
}

/** One thing an error has to say about a line, and whether it is the error itself or a part that explains it. */
data class Explanation(val message: String, val primary: Boolean)
