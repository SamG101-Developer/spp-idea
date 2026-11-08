package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppSpan
import com.github.samg101developer.sppidea.diagnostics.SppSymbol
import com.github.samg101developer.sppidea.diagnostics.SppSymbolLookup
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The compiler answers in lines and UTF-16 columns and the editor in offsets, and a mistake in between is the
 * difference between a click landing on a name and landing a character off it, or on nothing at all.
 */
class SppSymbolLookupTest : BasePlatformTestCase() {

    private val text = "fun probe() -> Void {\n  let got = make().expect(\"boom\")\n}\n"
    private val document = DocumentImpl(text)

    private fun span(line: Int, from: Int, to: Int) = SppSpan(
        file = "/x.spp", startLine = line, startCharacter = from, endLine = line, endCharacter = to,
        generated = false,
    )

    private fun symbolAt(line: Int, from: Int, to: Int, name: String) =
        SppSymbol("function", name, "", span(line, from, to), span(0, 0, 1))

    fun testRangeCoversExactlyTheName() {
        // "  let got = make().expect("boom")" - "expect" starts 19 characters into the second line.
        val range = SppSymbolLookup.rangeOf(span(1, 19, 25), document)
        assertNotNull(range)
        assertEquals("expect", document.getText(range!!))
    }

    fun testTheNameUnderTheCaretIsTheOneFound() {
        val symbols = listOf(symbolAt(1, 12, 16, "make"), symbolAt(1, 19, 25, "expect"))
        val offset = document.getLineStartOffset(1) + 20

        val found = SppSymbolLookup.symbolAt(symbols, offset, document)
        assertEquals("expect", found?.name)
    }

    fun testACaretOnNothingFindsNothing() {
        val symbols = listOf(symbolAt(1, 19, 25, "expect"))
        val offset = document.getLineStartOffset(1) + 2
        assertNull(SppSymbolLookup.symbolAt(symbols, offset, document))
    }

    fun testTheInnermostNameWins() {
        // A type's arguments sit inside the type, so the caret is in both spans at once.
        val outer = symbolAt(1, 12, 30, "outer")
        val inner = symbolAt(1, 19, 25, "expect")
        val offset = document.getLineStartOffset(1) + 20

        assertEquals("expect", SppSymbolLookup.symbolAt(listOf(outer, inner), offset, document)?.name)
    }

    fun testAPositionWithNoPlaceInTheSourceHasNoRange() {
        val nowhere = SppSpan(
            file = "/x.spp", startLine = 0, startCharacter = 0, endLine = 0, endCharacter = 0, generated = true,
        )
        assertNull(SppSymbolLookup.rangeOf(nowhere, document))
        assertNull(SppSymbolLookup.offsetOf(nowhere, document))
    }

    fun testTheWholeOfWhatIsAccessedIsTheReceiver() {
        // "  let got = make().expect(\"boom\")" - the call ends at 18, and so does the name "make" it was made from,
        // one being part of the other. What a "." after it applies to is the call.
        val name = SppSymbol("function", "make", "", span(1, 12, 16), span(0, 0, 1))
        val call = SppSymbol("expression", "", "Opt[S32]", span(1, 12, 18), span(0, 0, 1))
        val access = SppSymbolLookup.accessBefore(text, document.getLineStartOffset(1) + 19)!!

        val receiver = SppSymbolLookup.receiverOf(listOf(name, call), access, document)
        assertEquals("Opt[S32]", receiver?.type)
    }
}
