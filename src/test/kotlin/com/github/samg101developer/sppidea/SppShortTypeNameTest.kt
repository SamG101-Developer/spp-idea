package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppSymbolLookup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Shortening a type for the right-hand side of a completion list. The compiler writes types out in full, and what is
 * shown has to stay true to them: a borrow that loses its "&" says the opposite of what it means.
 */
class SppShortTypeNameTest {

    @Test
    fun `a borrowed type keeps its convention`() {
        assertEquals("&Str[A=GlobalAlloc]", SppSymbolLookup.shortTypeName("&std::string::Str[A=GlobalAlloc]"))
        assertEquals("&mut Str", SppSymbolLookup.shortTypeName("&mut std::string::Str"))
    }

    @Test
    fun `a generic argument is shortened too, not swallowed`() {
        assertEquals("Vec[T=S32]", SppSymbolLookup.shortTypeName("std::vector::Vec[T=std::number::S32]"))
        assertEquals(
            "Var[Variants=Tup[Some[T=S32], None]]",
            SppSymbolLookup.shortTypeName(
                "std::variant::Var[Variants=std::tuple::Tup[std::option::Some[T=std::number::S32], std::option::None]]",
            ),
        )
    }

    @Test
    fun `what is already short is left alone`() {
        assertEquals("S32", SppSymbolLookup.shortTypeName("S32"))
        assertEquals("", SppSymbolLookup.shortTypeName(""))
        assertEquals("SizedInteger[w=32_u32, signed=true]", SppSymbolLookup.shortTypeName(
            "std::num::sized_integer::SizedInteger[w=32_u32, signed=true]",
        ))
    }
}
