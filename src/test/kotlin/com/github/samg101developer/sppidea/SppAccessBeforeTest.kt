package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppSymbolLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading backwards from the caret to work out what is being completed. Everything here is a shape someone types, and
 * getting one wrong means either no list where there should be one, or a list of the wrong thing's members.
 */
class SppAccessBeforeTest {

    private fun accessIn(text: String) = SppSymbolLookup.accessBefore(text.replace("|", ""), text.indexOf('|'))

    @Test
    fun `a dot with nothing typed yet`() {
        val access = accessIn("let a = v.|")!!
        assertEquals(".", access.operator)
        assertEquals("", access.prefix)
        assertEquals("let a = v".length, access.receiverEnd)
    }

    @Test
    fun `a dot with part of the name typed`() {
        val access = accessIn("let a = v.pu|")!!
        assertEquals(".", access.operator)
        assertEquals("pu", access.prefix)
        assertEquals("let a = v".length, access.receiverEnd)
    }

    @Test
    fun `a double colon is read as one operator, not as a dot`() {
        val access = accessIn("std::mem::ops::|")!!
        assertEquals("::", access.operator)
        assertEquals("", access.prefix)
        assertEquals("std::mem::ops".length, access.receiverEnd)
    }

    @Test
    fun `a name being typed after a path`() {
        val access = accessIn("std::mem::ops::dr|")!!
        assertEquals("::", access.operator)
        assertEquals("dr", access.prefix)
        assertEquals("std::mem::ops".length, access.receiverEnd)
    }

    @Test
    fun `spaces around the access do not hide it`() {
        val access = accessIn("value . fi|")!!
        assertEquals(".", access.operator)
        assertEquals("fi", access.prefix)
        assertEquals("value".length, access.receiverEnd)
    }

    @Test
    fun `an underscore is part of a name, not the start of one`() {
        assertEquals("with_und", accessIn("v.with_und|")!!.prefix)
    }

    @Test
    fun `nothing is offered where no access was written`() {
        assertNull(accessIn("let a = v|"))
        assertNull(accessIn("|"))
        assertNull(accessIn("fun probe() -> Void {|"))
    }
}
