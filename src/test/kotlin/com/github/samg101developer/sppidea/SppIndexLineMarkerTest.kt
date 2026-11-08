package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppIndexLineMarkerProvider.IndexState
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl

/**
 * The gutter says whether a function's names have been worked out. A file the compiler has never been run over is
 * "pending", which is the state that matters most: it is what tells a reader that "no declaration found" means "not
 * yet" rather than "not there".
 */
class SppIndexLineMarkerTest : BasePlatformTestCase() {

    // Files on the real file system, not the in-memory ones a light fixture makes: a mark is only shown for a file
    // the compiler could be run over, which means one with a path.
    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    fun testEveryFunctionIsMarked() {
        myFixture.addFileToProject("marked.spp", "fun first() -> Void { }\n\ncor second() -> Gen[S32] { }\n")
        myFixture.configureFromTempProjectFile("marked.spp")
        myFixture.doHighlighting()

        val gutters = myFixture.findAllGutters()
        assertEquals("one mark per function", 2, gutters.size)
        gutters.forEach { assertTrue(it.tooltipText.orEmpty().startsWith("Not analysed yet")) }
    }

    fun testNothingButFunctionsIsMarked() {
        myFixture.addFileToProject("one.spp", "cls Thing { }\n\nfun only_one() -> Void {\n    let x = 1\n}\n")
        myFixture.configureFromTempProjectFile("one.spp")
        myFixture.doHighlighting()

        // The class and the local are not functions, so the one mark belongs to "only_one".
        assertEquals(1, myFixture.findAllGutters().size)
    }

    fun testWhatEachSituationShows() {
        // A run under way is said whatever else is true: the point of the mark is telling "no answer" from "not yet".
        assertEquals(IndexState.ANALYSING, IndexState.of(analysing = true, analysed = false, failed = false))
        assertEquals(IndexState.ANALYSING, IndexState.of(analysing = true, analysed = true, failed = false))
        assertEquals(IndexState.ANALYSING, IndexState.of(analysing = true, analysed = true, failed = true))

        // With nothing running, what is known decides it.
        assertEquals(IndexState.PENDING, IndexState.of(analysing = false, analysed = false, failed = false))
        assertEquals(IndexState.INDEXED, IndexState.of(analysing = false, analysed = true, failed = false))
        assertEquals(IndexState.INCOMPLETE, IndexState.of(analysing = false, analysed = true, failed = true))
    }
}
