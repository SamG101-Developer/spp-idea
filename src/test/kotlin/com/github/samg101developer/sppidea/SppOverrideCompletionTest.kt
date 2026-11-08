package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppCompilerDiagnostics
import com.github.samg101developer.sppidea.diagnostics.SppDiagnosticParser
import com.github.samg101developer.sppidea.diagnostics.SppOverridable
import com.github.samg101developer.sppidea.diagnostics.SppOverridableList
import com.github.samg101developer.sppidea.diagnostics.SppOverrideLookup
import com.github.samg101developer.sppidea.diagnostics.SppSpan
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import kotlin.io.path.absolute

/**
 * Between the members of a "sup Name ext SuperCls" block, completion offers the superclass's abstract and virtual
 * methods that the block has not written yet, and writes the chosen one out as a whole method to fill in.
 */
class SppOverrideCompletionTest : BasePlatformTestCase() {

    // Files on disk: completion only answers for a file with a path, which is what the compiler's answers are keyed by.
    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    private val area = SppOverridable("area", "fun area(&self) -> S32", "abstract", null)
    private val describe = SppOverridable("describe", "fun describe(&self) -> Void", "virtual", null)

    private fun span(line: Int) = SppSpan("/x.spp", line, 0, line + 2, 1, generated = false)

    private fun shapes(body: String) = "cls Shape { }\n\ncls Circle { }\nsup Circle ext Shape {\n$body\n}\n"

    // Open [text] as a file, with the last analysis having said the Circle block can override [methods].
    private fun open(text: String, methods: List<SppOverridable> = listOf(area, describe)) {
        myFixture.addFileToProject("shapes.spp", text)
        myFixture.configureFromTempProjectFile("shapes.spp")
        val path = myFixture.file.virtualFile.toNioPath().absolute()
        SppCompilerDiagnostics.getInstance(project).seedOverridables(
            path, listOf(SppOverridableList(span(3), "Circle", "Shape", methods)),
        )
    }

    fun testTheMethodsLeftToOverrideAreOffered() {
        open(shapes("  <caret>"))
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, area.prototype, describe.prototype)
        // The abstract method has to be written, so it comes first.
        assertEquals(area.prototype, myFixture.lookupElementStrings!!.first())
    }

    fun testChoosingOneWritesTheWholeMethod() {
        open(shapes("  <caret>"))
        myFixture.completeBasic()
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == area.prototype }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        myFixture.checkResult(shapes("  fun area(&self) -> S32 {\n    <caret>\n  }"))
    }

    fun testAMethodAlreadyWrittenIsNotOfferedAgain() {
        open(shapes("  fun describe(&self) -> Void { }\n\n  <caret>"))
        myFixture.completeBasic()
        assertEquals(listOf(area.prototype), myFixture.lookupElementStrings)
    }

    fun testTheStartOfAHeaderNarrowsAndIsReplaced() {
        open(shapes("  fun ar<caret>"))
        myFixture.completeBasic()
        myFixture.checkResult(shapes("  fun area(&self) -> S32 {\n    <caret>\n  }"))
    }

    fun testNothingIsOfferedInsideAMethodBody() {
        open(shapes("  fun describe(&self) -> Void {\n    <caret>\n  }"))
        myFixture.completeBasic()
        assertFalse(myFixture.lookupElementStrings.orEmpty().contains(area.prototype))
    }

    fun testTheBlockAroundTheCaretIsFoundFromTheText() {
        val text = "sup [T] Box[T] ext Shape {  # { not a brace\n  fun f() -> Void {\n    \n  }\n  \n}\n"
        val inBody = text.indexOf("    \n") + 4
        val betweenMembers = text.indexOf("  \n}") + 2
        assertNull("a method body is not between the block's members", SppOverrideLookup.enclosingBlock(text, inBody))
        val block = SppOverrideLookup.enclosingBlock(text, betweenMembers)!!
        assertEquals("Box[T]", block.name)
        assertEquals("Shape", block.superclass)
        assertNull("a plain sup block has no superclass", SppOverrideLookup.enclosingBlock("sup Box {\n  ", 12))
    }

    fun testABlockTheAnalysisHasNotSeenHasNoAnswer() {
        val text = "sup Square ext Shape {\n  "
        val block = SppOverrideLookup.enclosingBlock(text, text.length)!!
        val known = listOf(SppOverridableList(span(0), "Circle", "Shape", listOf(area)))
        assertNull(SppOverrideLookup.pending(known, block, text, DocumentImpl(text)))
    }

    fun testTheCompilersRecordIsRead() {
        val json = """{"kind":"overridables","where":{"file":"/p/a.spp","start":{"line":3,"character":0},"end":{"line":5,"character":1}},"name":"Circle","superclass":"Shape","methods":[{"name":"area","prototype":"fun area(&self) -> S32","modifier":"abstract","definition":{"file":"/p/a.spp","start":{"line":1,"character":8},"end":{"line":1,"character":12}}}]}"""
        val list = SppDiagnosticParser.parseOverridables(json).single()
        assertEquals("Circle", list.name)
        assertEquals("Shape", list.superclass)
        assertEquals(3, list.where.startLine)
        assertEquals(listOf("fun area(&self) -> S32"), list.methods.map { it.prototype })
        assertEquals("abstract", list.methods.single().modifier)
    }
}
