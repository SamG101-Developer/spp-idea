package com.github.samg101developer.sppidea

import com.intellij.lang.SppDocstringFillPostFormatProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How docstring lines are re-wrapped at the right margin. Wrapped lines of a tag paragraph ("@let ...") hang by the tab
 * width so the tag stands out; everything else wraps flush with the "# ".
 */
class SppDocstringReflowTest {

    private fun reflow(lines: List<String>, margin: Int = 30, tab: Int = 2) =
        SppDocstringFillPostFormatProcessor.reflowLines(lines, margin, tab)

    @Test
    fun `wrapped tag lines are indented by the tab width`() {
        val result = reflow(listOf("  # @let x the value that is used for the whole computation"))
        assertEquals(
            listOf(
                "  # @let x the value that is",
                "  #   used for the whole",
                "  #   computation",
            ),
            result,
        )
    }

    @Test
    fun `hanging indent stops at the next tag`() {
        val result = reflow(
            listOf(
                "# @let a first value which wraps over",
                "# @let b second",
            ),
        )
        assertEquals(
            listOf(
                "# @let a first value which",
                "#   wraps over",
                "# @let b second",
            ),
            result,
        )
    }

    @Test
    fun `plain text after a blank line is not indented`() {
        val result = reflow(
            listOf(
                "# @let a short",
                "#",
                "# Plain text that wraps across more than one line.",
            ),
        )
        assertEquals(
            listOf(
                "# @let a short",
                "#",
                "# Plain text that wraps across",
                "# more than one line.",
            ),
            result,
        )
    }

    @Test
    fun `already hanging-indented tag text is stable`() {
        val lines = listOf(
            "# @let x the value that is",
            "#   used for the whole",
            "#   computation",
        )
        assertNull(reflow(lines))
    }

    @Test
    fun `tab width setting is respected`() {
        val result = reflow(listOf("# @let x the value that is used for everything"), tab = 4)
        assertEquals(
            listOf(
                "# @let x the value that is",
                "#     used for everything",
            ),
            result,
        )
    }

    @Test
    fun `wrapped bullet lines are indented by the tab width`() {
        val result = reflow(listOf("# - a bullet point that wraps over several lines"))
        assertEquals(
            listOf(
                "# - a bullet point that wraps",
                "#   over several lines",
            ),
            result,
        )
    }

    @Test
    fun `a bullet's existing continuation lines are joined back onto it`() {
        val result = reflow(
            listOf(
                "# - first item that",
                "# wraps badly",
                "# * second",
                "# 1. numbered item that wraps over",
            ),
        )
        assertEquals(
            listOf(
                "# - first item that wraps",
                "#   badly",
                "# * second",
                "# 1. numbered item that wraps",
                "#   over",
            ),
            result,
        )
    }

    @Test
    fun `already hanging-indented bullet text is stable`() {
        val lines = listOf(
            "# - a bullet point that wraps",
            "#   over several lines",
            "# - another",
        )
        assertNull(reflow(lines))
    }
}
