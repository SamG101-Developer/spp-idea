package com.github.samg101developer.sppidea

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A value moved onto the line after its "=", or an operand moved onto the line after its operator, is indented one
 * level under the statement it belongs to. A chain of operators stays at that one level.
 */
class SppAssignIndentTest : BasePlatformTestCase() {

    private fun reformat(source: String): String {
        val file = myFixture.configureByText("assign.spp", source)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(file)
        }
        return myFixture.editor.document.text
    }

    fun testLetValueOnNextLine() = assertEquals(
        "fun f() -> Void {\n  let x =\n    123\n}\n",
        reformat("fun f() -> Void {\n  let x =\n  123\n}\n"),
    )

    fun testAssignmentValueOnNextLine() = assertEquals(
        "fun f() -> Void {\n  variable =\n    123\n}\n",
        reformat("fun f() -> Void {\n  variable =\n123\n}\n"),
    )

    fun testExplicitTypeLetValueOnNextLine() = assertEquals(
        "fun f() -> Void {\n  let x: S32 =\n    123_s32\n}\n",
        reformat("fun f() -> Void {\n  let x: S32 =\n      123_s32\n}\n"),
    )

    fun testSameLineValueIsUnchanged() = assertEquals(
        "fun f() -> Void {\n  let x = 123\n}\n",
        reformat("fun f() -> Void {\n  let x = 123\n}\n"),
    )

    private fun body(vararg lines: String) = "fun f() -> Void {\n" + lines.joinToString("") { "$it\n" } + "}\n"

    fun testOperandAfterTrailingOperator() = assertEquals(
        body("  let x = a +", "    b"),
        reformat(body("  let x = a +", "b")),
    )

    fun testTrailingOperatorChainStaysAtOneLevel() = assertEquals(
        body("  let x = a +", "    b +", "    c"),
        reformat(body("  let x = a +", "b +", "c")),
    )

    fun testLeadingOperatorChainStaysAtOneLevel() = assertEquals(
        body("  let x = a", "    + b", "    + c"),
        reformat(body("  let x = a", "+ b", "+ c")),
    )

    fun testCompoundAssignmentValueOnNextLine() = assertEquals(
        body("  x +=", "    1"),
        reformat(body("  x +=", "1")),
    )

    fun testBooleanOperatorsOnNextLine() = assertEquals(
        body("  let ok = a and", "    b or", "    c"),
        reformat(body("  let ok = a and", "b or", "c")),
    )

    fun testChainBreakingAfterSeveralOperandsOnFirstLine() = assertEquals(
        body("  let x = a + b +", "    c +", "    d"),
        reformat(body("  let x = a + b +", "c +", "d")),
    )

    fun testReturnTypeOnItsOwnLine() = assertEquals(
        "fun f(\n  a: S32, b: S32)\n  -> Void {\n  ret\n}\n",
        reformat("fun f(\n  a: S32, b: S32)\n-> Void {\n  ret\n}\n"),
    )

    fun testAnnotatedReturnTypeOnItsOwnLine() = assertEquals(
        "!public\nfun f(a: S32)\n  -> Void {\n  ret\n}\n",
        reformat("!public\nfun f(a: S32)\n    -> Void {\n  ret\n}\n"),
    )

    fun testReturnTypeAfterArrowOnItsOwnLine() = assertEquals(
        "fun f(a: S32) ->\n  Void {\n  ret\n}\n",
        reformat("fun f(a: S32) ->\nVoid {\n  ret\n}\n"),
    )
}
