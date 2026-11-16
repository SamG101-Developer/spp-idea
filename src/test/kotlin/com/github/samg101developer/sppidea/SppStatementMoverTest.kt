package com.github.samg101developer.sppidea

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Ctrl+shift+up/down on the header line of a block (fun, cls, loop, case, ...) moves the whole block past its
 * neighbour; on any other line it moves just that line.
 */
class SppStatementMoverTest : BasePlatformTestCase() {

    private fun move(before: String, after: String, down: Boolean) {
        myFixture.configureByText("move.spp", before)
        myFixture.performEditorAction(
            if (down) IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION else IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION
        )
        myFixture.checkResult(after)
    }

    private val f = "fun f() -> Void {\n  let a = 1\n}\n"
    private val g = "fun g() -> Void {\n  let b = 2\n}\n"

    fun testFunctionMovesDownPastFunction() =
        move("fun <caret>f() -> Void {\n  let a = 1\n}\n$g", "${g}fun <caret>f() -> Void {\n  let a = 1\n}\n", down = true)

    fun testFunctionMovesUpPastFunction() =
        move("${f}fun <caret>g() -> Void {\n  let b = 2\n}\n", "fun <caret>g() -> Void {\n  let b = 2\n}\n$f", down = false)

    fun testBlankLineIsSteppedOver() =
        move("fun <caret>f() -> Void {\n  let a = 1\n}\n\n$g", "\nfun <caret>f() -> Void {\n  let a = 1\n}\n$g", down = true)

    fun testAnnotationAndCommentsTravelWithBlock() =
        move(
            "# doc\n!public\nfun <caret>f() -> Void {\n  let a = 1\n}\n$g",
            "$g# doc\n!public\nfun <caret>f() -> Void {\n  let a = 1\n}\n",
            down = true
        )

    fun testLoopMovesPastStatement() = move(
        "fun f() -> Void {\n  loop <caret>true {\n    let a = 1\n  }\n  let b = 2\n}\n",
        "fun f() -> Void {\n  let b = 2\n  loop <caret>true {\n    let a = 1\n  }\n}\n",
        down = true
    )

    fun testBlockDoesNotLeaveItsScope() {
        val source = "fun f() -> Void {\n  loop <caret>true {\n    let a = 1\n  }\n}\n"
        move(source, source, down = true)
    }

    fun testInnerLineStillMovesAsLine() = move(
        "fun f() -> Void {\n  let <caret>a = 1\n  let b = 2\n}\n",
        "fun f() -> Void {\n  let b = 2\n  let <caret>a = 1\n}\n",
        down = true
    )
}
