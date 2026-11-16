package com.github.samg101developer.sppidea

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The "." and "::" access operators lose any spaces either side when reformatted, in expressions, use paths and
 * types. A chain continued on a new line keeps its line break before the operator.
 */
class SppAccessSpacingTest : BasePlatformTestCase() {

    private fun reformat(source: String): String {
        val file = myFixture.configureByText("access.spp", source)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(file)
        }
        return myFixture.editor.document.text
    }

    private fun body(statement: String) = "fun f() -> Void {\n  $statement\n}\n"

    fun testRuntimeAccess() = assertEquals(body("let x = a.b.c"), reformat(body("let x = a . b .c")))

    fun testStaticAccess() = assertEquals(body("let x = a::b::c()"), reformat(body("let x = a :: b:: c()")))

    fun testMethodCallChain() = assertEquals(body("let x = a.b().c(1)"), reformat(body("let x = a .b() . c(1)")))

    fun testKeywordNot() = assertEquals(body("let x = a.not"), reformat(body("let x = a . not")))

    fun testAssignmentTarget() = assertEquals(body("self.x = 1"), reformat(body("self . x = 1")))

    fun testTypeNamespace() = assertEquals(body("let x: std::vector::Vec[S32] = y"), reformat(body("let x: std :: vector ::Vec[S32] = y")))

    fun testUseStatement() = assertEquals("use std::vector::Vec\n", reformat("use std :: vector :: Vec\n"))

    fun testChainLineBreakKept() = assertEquals(
        body("let x = a\n    .b()\n    .c()"),
        reformat(body("let x = a\n    . b()\n    .  c()")),
    )

    fun testLineBreakAfterOperatorRemoved() = assertEquals(body("let x = a.b"), reformat(body("let x = a.\n  b")))
}
