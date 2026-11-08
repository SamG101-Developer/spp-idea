package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppDiagnostic
import com.github.samg101developer.sppidea.diagnostics.SppDiagnosticParser
import com.github.samg101developer.sppidea.diagnostics.SppDiagnosticTooltip
import com.github.samg101developer.sppidea.diagnostics.SppLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What hovering an error shows. The tooltip is the only place the note, the help and the other places an error points
 * at are all visible together, so each must be there, told apart, and the places must be links to jump to.
 */
class SppDiagnosticTooltipTest {

    private fun label(file: String, line: Int, message: String, primary: Boolean = false, generated: Boolean = false) =
        SppLabel(file, message, primary, line, 0, line, 3, generated)

    private val here = "/p/src/main.spp"
    private val error = label(here, 4, "Found type Std::Str", primary = true)
    private val expected = label("/p/src/other.spp", 9, "Expected type S32")
    private val mismatch = SppDiagnostic(
        code = "E1",
        title = "Type Mismatch Error",
        severity = "error",
        labels = listOf(expected, error),
        note = "The two types are not\nsymbolically equal.",
        help = "Change one <of> them.",
    )

    private fun render(d: SppDiagnostic = mismatch, current: SppLabel = error, offsets: (SppLabel) -> Int? = { 42 }) =
        SppDiagnosticTooltip.render(d, current, here, offsets)

    @Test
    fun `header, message, note and help are all shown and labelled`() {
        val html = render()
        assertTrue(html, html.startsWith("<html>"))
        assertTrue(html, "<b>Type Mismatch Error</b>" in html)
        assertTrue(html, "E1" in html)
        assertTrue(html, "Found type Std::Str" in html)
        assertTrue(html, "<b>Note:</b>" in html)
        assertTrue(html, "<b>Help:</b>" in html)
    }

    @Test
    fun `terminal line breaks in the note are joined`() {
        assertTrue("The two types are not symbolically equal." in render())
    }

    @Test
    fun `compiler text is escaped`() {
        val html = render()
        assertTrue(html, "Change one &lt;of&gt; them." in html)
        assertFalse(html, "<of>" in html)
    }

    @Test
    fun `other places link to where they are`() {
        val html = render()
        assertTrue(html, "href=\"#navigation//p/src/other.spp:42\"" in html)
        assertTrue(html, ">other.spp:10<" in html)
        assertTrue(html, "Expected type S32" in html)
    }

    @Test
    fun `a place that can't be found is not a link`() {
        val html = render(offsets = { null })
        assertTrue(html, "other.spp:10" in html)
        assertFalse(html, "#navigation" in html)
    }

    @Test
    fun `hovering an explanation lists the error itself in bold`() {
        val html = render(current = expected)
        assertTrue(html, "<b>Found type Std::Str</b>" in html)
        assertTrue(html, ">line 5<" in html)
    }

    @Test
    fun `generated places are left out`() {
        val hidden = label(here, 0, "macro internals", generated = true)
        val html = render(mismatch.copy(labels = mismatch.labels + hidden))
        assertFalse(html, "macro internals" in html)
    }

    @Test
    fun `a wrapped error is shown verbatim`() {
        val html = render(mismatch.copy(wrapped = listOf("E26 Identifier Unknown Error\n  |  let x = y")))
        assertTrue(html, "<b>Wrapped error:</b>" in html)
        assertTrue(html, "<pre>E26 Identifier Unknown Error\n  |  let x = y</pre>" in html)
    }

    @Test
    fun `the wrapped field is read from the compiler`() {
        val json = """{"kind":"diagnostic","severity":"error","code":"E1001","title":"Generated Code Error","labels":[],"note":"","help":"","wrapped":["inner error",""]}"""
        assertEquals(listOf("inner error"), SppDiagnosticParser.parse(json).single().wrapped)
    }

    @Test
    fun `backticked spans are shown as code`() {
        val html = render(
            mismatch.copy(
                labels = listOf(expected, error.copy(message = "Found type `Std::Str`")),
                note = "The `identifier` of `x` is unknown.",
            ),
        )
        assertTrue(html, "Found type <code>Std::Str</code>" in html)
        assertTrue(html, "The <code>identifier</code> of <code>x</code> is unknown." in html)
        assertFalse(html, "`" in html)
    }

    @Test
    fun `code inside the bold error line stays code`() {
        val html = render(mismatch.copy(labels = listOf(expected, error.copy(message = "Found type `S32`"))), current = expected)
        assertTrue(html, "<b>Found type <code>S32</code></b>" in html)
    }

    @Test
    fun `an unpaired backtick is kept as text`() {
        assertEquals("a <code>b</code> c `d", SppDiagnosticTooltip.withCode("a `b` c `d").toString())
    }

    @Test
    fun `code is escaped too`() {
        assertEquals("<code>Vec&lt;T&gt;</code>", SppDiagnosticTooltip.withCode("`Vec<T>`").toString())
    }

    @Test
    fun `the problems view message has the code, title and message`() {
        assertEquals("E1 Type Mismatch Error: Found type Std::Str", SppDiagnosticTooltip.description(mismatch, error))
    }
}
