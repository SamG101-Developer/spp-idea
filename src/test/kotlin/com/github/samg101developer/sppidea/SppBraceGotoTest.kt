package com.github.samg101developer.sppidea

import com.intellij.lang.matchingBrace
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Ctrl+clicking a bracket goes to the bracket that matches it, in either direction, past any nested brackets.
 */
class SppBraceGotoTest : BasePlatformTestCase() {

    private val source = "fun f(a: Vec[Vec[S32]]) -> Void {\n    if true { g((1), [2]) }\n}\n"

    private fun matchOf(index: Int): Int? {
        val file = myFixture.configureByText("braces.spp", source)
        val brace = file.findElementAt(index)!!
        return matchingBrace(brace)?.textOffset
    }

    private fun assertPair(open: Int, close: Int) {
        assertEquals(close, matchOf(open))
        assertEquals(open, matchOf(close))
    }

    fun testCurlyBraces() = assertPair(source.indexOf('{'), source.lastIndexOf('}'))

    fun testNestedCurlyBraces() = assertPair(source.indexOf("{ g"), source.indexOf("}\n}"))

    fun testParentheses() = assertPair(source.indexOf('('), source.indexOf(')'))

    fun testNestedSquareBrackets() = assertPair(source.indexOf('['), source.indexOf("]]") + 1)

    fun testNotABrace() = assertNull(matchOf(source.indexOf("Void")))
}
