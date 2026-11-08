package com.github.samg101developer.sppidea

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A docstring line starting with "@" must use a tag that means something where it is written. A misspelt tag is
 * otherwise silently dropped from the rendered documentation, so the reader never learns the parameter was documented.
 */
class SppDocstringTagTest : BasePlatformTestCase() {

    private fun tagWarnings(text: String): List<String> {
        myFixture.configureByText("tags.spp", text)
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { "docstring" in it }
    }

    fun testKnownFunctionTagsAreAccepted() {
        val warnings = tagWarnings(
            "fun f(a: S32) -> S32 {\n  # Doubles it.\n  # @let a: the value\n  # @ret: twice [a]\n  ret a\n}\n"
        )
        assertEmpty(warnings)
    }

    fun testUnknownTagIsFlagged() {
        val warnings = tagWarnings("fun f(a: S32) -> Void {\n  # @let a: the value\n  # @param a: again\n}\n")
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), warnings.single().startsWith("Unknown docstring tag '@param'"))
    }

    fun testTagInTheWrongPlaceIsFlagged() {
        val warnings = tagWarnings("cls Point {\n  # A point.\n  # @ret: nothing\n  x: S32\n}\n")
        assertTrue(warnings.toString(), warnings.any { it.startsWith("'@ret' is not valid in a class docstring") })
    }

    fun testTagsInsideCodeBlocksAndMidSentenceAreIgnored() {
        val warnings = tagWarnings(
            "fun f() -> Void {\n  # Mail me@example.com.\n  # ```\n  # @nonsense\n  # ```\n}\n"
        )
        assertEmpty(warnings)
    }
}
