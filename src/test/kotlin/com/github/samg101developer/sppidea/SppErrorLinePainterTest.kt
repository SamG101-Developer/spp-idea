package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppDiagnostic
import com.github.samg101developer.sppidea.diagnostics.Explanation
import com.github.samg101developer.sppidea.diagnostics.SppLabel
import com.github.samg101developer.sppidea.diagnostics.explanationsOn
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Choosing what an error has to say at the end of a line. Only the parts that explain it, only for this file, and
 * never at the place the error is already reported with a squiggle.
 */
class SppErrorLinePainterTest {

    private fun label(message: String, line: Int, primary: Boolean, file: String = "/a.spp", generated: Boolean = false) =
        SppLabel(file, message, primary, line, 0, line, 4, generated)

    private fun diagnostic(vararg labels: SppLabel) =
        SppDiagnostic("E7", "Move Error", "error", labels.toList(), "", "")

    @Test
    fun `the context of an error is written on the line it points at`() {
        val diagnostics = listOf(diagnostic(label("moved here", 3, primary = false), label("used here", 9, primary = true)))
        assertEquals(listOf(Explanation("moved here", false)), explanationsOn(diagnostics, "/a.spp", 3))
    }

    @Test
    fun `the line the error is reported on says what is wrong, marked as the error itself`() {
        val diagnostics = listOf(diagnostic(label("used here", 9, primary = true)))
        assertEquals(listOf(Explanation("used here", true)), explanationsOn(diagnostics, "/a.spp", 9))
    }

    @Test
    fun `a line that is both says what is wrong first`() {
        val diagnostics = listOf(
            diagnostic(label("moved here", 4, primary = false)),
            diagnostic(label("used after the move", 4, primary = true)),
        )
        assertEquals(
            listOf(Explanation("used after the move", true), Explanation("moved here", false)),
          explanationsOn(diagnostics, "/a.spp", 4),
        )
    }

    @Test
    fun `another file's error is left to that file`() {
        val diagnostics = listOf(diagnostic(label("moved here", 3, primary = false, file = "/b.spp")))
        assertEquals(emptyList<Explanation>(), explanationsOn(diagnostics, "/a.spp", 3))
    }

    @Test
    fun `a place that is not in the source is not written anywhere`() {
        val diagnostics = listOf(diagnostic(label("in generated code", 0, primary = false, generated = true)))
        assertEquals(emptyList<Explanation>(), explanationsOn(diagnostics, "/a.spp", 0))
    }

    @Test
    fun `two errors explaining the same line say it once`() {
        val diagnostics = listOf(
            diagnostic(label("moved here", 3, primary = false)),
            diagnostic(label("moved here", 3, primary = false)),
        )
        assertEquals(listOf(Explanation("moved here", false)), explanationsOn(diagnostics, "/a.spp", 3))
    }
}
