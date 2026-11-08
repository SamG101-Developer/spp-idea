package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppSymbolLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a recorded signature for what a caller writes. The receiver is not one of those things, and getting that
 * wrong leaves the caret sitting inside empty brackets on every method with no arguments.
 */
class SppCallSignatureTest {

    @Test
    fun `the receiver is not an argument`() {
        assertEquals(emptyList<String>(), SppSymbolLookup.callParameters("(&mut self) -> Opt[T]"))
        assertEquals(emptyList<String>(), SppSymbolLookup.callParameters("(self) -> Self"))
        assertFalse(SppSymbolLookup.callTakesArguments("(&self) -> USize"))
    }

    @Test
    fun `what follows the receiver is`() {
        assertEquals(listOf("element: T"), SppSymbolLookup.callParameters("(&mut self, element: T) -> Void"))
        assertTrue(SppSymbolLookup.callTakesArguments("(&mut self, element: T) -> Void"))
    }

    @Test
    fun `a free function has no receiver to drop`() {
        assertEquals(listOf("val: T"), SppSymbolLookup.callParameters("(val: T) -> Void"))
        assertEquals(emptyList<String>(), SppSymbolLookup.callParameters("() -> Void"))
    }

    @Test
    fun `a parameter named like the receiver but later is kept`() {
        assertEquals(listOf("self_like: T"), SppSymbolLookup.callParameters("(&self, self_like: T) -> Void"))
    }
}
