package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppMember
import com.github.samg101developer.sppidea.diagnostics.SppScope
import com.github.samg101developer.sppidea.diagnostics.SppSignature
import com.github.samg101developer.sppidea.diagnostics.SppSpan
import com.github.samg101developer.sppidea.diagnostics.SppSymbolLookup
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Working out what a caret is inside: the brackets of a call, and the scopes covering it. Both decide what a
 * completion list holds where nothing is being accessed.
 */
class SppArgumentLookupTest : BasePlatformTestCase() {

    private fun span(fromLine: Int, from: Int, toLine: Int, to: Int) =
        SppSpan("/x.spp", fromLine, from, toLine, to, generated = false)

    private fun member(name: String) = SppMember(name, "parameter", "S32", null)

    fun testTheBracketTheCaretIsInside() {
        val text = "f(g(x), "
        // Inside "g(" the answer is g's bracket; after it closes, f's.
        assertEquals(3, SppSymbolLookup.enclosingOpenBracket(text, 4))
        assertEquals(1, SppSymbolLookup.enclosingOpenBracket(text, text.length))
        assertNull(SppSymbolLookup.enclosingOpenBracket("f(x)", 4))
        assertNull(SppSymbolLookup.enclosingOpenBracket("let a = 1", 9))
    }

    fun testTheNameBeforeTheBracket() {
        assertEquals("Point".length, SppSymbolLookup.calleeEnd("Point (", 6))
        assertEquals("shifted".length, SppSymbolLookup.calleeEnd("shifted(", 7))
    }

    fun testTheCallWhoseArgumentsTheCaretIsIn() {
        val document = DocumentImpl("let n = shifted(base=1, by=2)\n")
        val outer = SppSignature("shifted", span(0, 15, 0, 29), listOf(member("base"), member("by")))
        val elsewhere = SppSignature("other", span(0, 0, 0, 3), listOf(member("nope")))

        val found = SppSymbolLookup.signatureAt(listOf(outer, elsewhere), 20, document)
        assertEquals("shifted", found?.name)
        assertNull(SppSymbolLookup.signatureAt(listOf(outer), 5, document))
    }

    fun testEveryScopeCoveringTheCaret() {
        val document = DocumentImpl("fun probe() -> Void {\n    let a = 1\n}\n")
        val module = SppScope(span(0, 0, 2, 1), listOf(member("probe")))
        val body = SppScope(span(0, 20, 2, 1), listOf(member("a")))
        val elsewhere = SppScope(span(0, 0, 0, 3), listOf(member("nope")))

        // A caret in the body is in the module as well, and both sets can be written there.
        val names = SppSymbolLookup.namesInScopeAt(listOf(body, module, elsewhere), 30, document).map { it.name }
        assertEquals(listOf("probe", "a"), names)
    }
}
