package com.github.samg101developer.sppidea

import com.intellij.lang.SppDocstringRefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What counts as a "[name]" parameter reference in a docstring line. The highlighting, the validation, the wrapping and
 * the rendered documentation all read references through this, so a bracket it wrongly takes for one is coloured,
 * warned about and rendered as a parameter everywhere at once.
 */
class SppDocstringRefsTest {

    @Test
    fun `a bracketed name is a reference`() {
        val refs = SppDocstringRefs.findIn("# Copies [count] bytes from [source].")
        assertEquals(listOf("count", "source"), refs.map { it.name })
        assertEquals("[count]", "# Copies [count] bytes from [source].".substring(refs[0].range))
        assertEquals("count", "# Copies [count] bytes from [source].".substring(refs[0].nameRange))
    }

    @Test
    fun `generic arguments and indexing are not references`() {
        assertEquals(emptyList<String>(), SppDocstringRefs.findIn("# Returns a Vec[T] of the items.").map { it.name })
        assertEquals(emptyList<String>(), SppDocstringRefs.findIn("# Reads data[i] then grid[x][y].").map { it.name })
    }

    @Test
    fun `a link and inline code are not references`() {
        assertEquals(emptyList<String>(), SppDocstringRefs.findIn("# See [docs](https://example.com).").map { it.name })
        assertEquals(emptyList<String>(), SppDocstringRefs.findIn("# Call it as `f([x])`.").map { it.name })
    }

    @Test
    fun `only a name in brackets is a reference`() {
        assertEquals(emptyList<String>(), SppDocstringRefs.findIn("# The pair [1, 2] or [a b].").map { it.name })
        assertEquals(listOf("_inner2"), SppDocstringRefs.findIn("# Uses [_inner2].").map { it.name })
    }

    @Test
    fun `a renderer finds a reference only where one starts`() {
        val text = "see [x] and v[y]"
        assertEquals("x", SppDocstringRefs.refAt(text, 4)?.name)
        assertNull(SppDocstringRefs.refAt(text, 13))
        assertNull(SppDocstringRefs.refAt(text, 0))
    }

    @Test
    fun `a fence line is recognised with or without the leading space`() {
        assertEquals(true, SppDocstringRefs.isFence("# ```"))
        assertEquals(true, SppDocstringRefs.isFence("#```spp"))
        assertEquals(false, SppDocstringRefs.isFence("# text with ``` inside"))
    }
}
