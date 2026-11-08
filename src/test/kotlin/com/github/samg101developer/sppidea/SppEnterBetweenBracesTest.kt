package com.github.samg101developer.sppidea

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Pressing enter between an empty pair of braces opens a blank line between them, with the closing brace on its own
 * line below.
 */
class SppEnterBetweenBracesTest : BasePlatformTestCase() {

    private fun enter(source: String): String {
        myFixture.configureByText("enter.spp", source)
        myFixture.type('\n')
        return myFixture.editor.document.text.let {
            val caret = myFixture.editor.caretModel.offset
            it.substring(0, caret) + "<caret>" + it.substring(caret)
        }
    }

    fun testFunctionBody() = assertEquals(
        "fun f() -> Void {\n  <caret>\n}\n",
        enter("fun f() -> Void {<caret>}\n"),
    )

    fun testCollapsedEmptyBody() = assertEquals(
        "fun f() -> Void {\n  <caret>\n}\n",
        enter("fun f() -> Void {<caret> }\n"),
    )

    fun testTypedOpeningBrace() {
        myFixture.configureByText("enter.spp", "fun f() -> Void <caret>\n")
        myFixture.type("{\n")
        assertEquals("fun f() -> Void {\n  \n}\n", myFixture.editor.document.text)
    }

    fun testNestedScope() = assertEquals(
        "fun f() -> Void {\n  case true {\n    <caret>\n  }\n}\n",
        enter("fun f() -> Void {\n  case true {<caret>}\n}\n"),
    )

    fun testClassBody() = assertEquals(
        "cls A {\n  <caret>\n}\n",
        enter("cls A {<caret>}\n"),
    )
}
