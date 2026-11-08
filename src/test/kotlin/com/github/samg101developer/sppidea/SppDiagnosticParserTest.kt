package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.diagnostics.SppDiagnosticParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is fed what the compiler actually writes, copied from a run of
 * `spp build --analyse-only --message-format=json`, so that a change to either side shows up here rather than as
 * annotations quietly going missing.
 */
class SppDiagnosticParserTest {

    @Test
    fun `reads who can reach each member`() {
        val members = SppDiagnosticParser.parseMembers(MEMBERS_WITH_VISIBILITY).single().members
        assertEquals(listOf("open" to "public", "hidden" to "private"), members.map { it.name to it.visibility })
    }

    @Test
    fun `reads a semantic error with its context label`() {
        val diagnostics = SppDiagnosticParser.parse(SEMANTIC_ERROR)
        assertEquals(1, diagnostics.size)

        val diagnostic = diagnostics.single()
        assertEquals("E111", diagnostic.code)
        assertEquals("error", diagnostic.severity)
        assertEquals("Superimposition External Marker Extension Error", diagnostic.title)
        assertTrue(diagnostic.note.startsWith("A marker decides"))
        assertTrue(diagnostic.help.startsWith("Superimpose the marker"))

        // The place the error is reported at, which is what gets underlined.
        val primary = diagnostic.primaryLabel!!
        assertEquals("Marker superimposed from outside its package here", primary.message)
        assertEquals(0, primary.startLine)
        assertEquals(13, primary.startCharacter)
        assertEquals(17, primary.endCharacter)
        assertEquals("/home/sam/spp/project/src/testing/zz_idea_probe.spp", primary.file)

        // And the place that explains it, which becomes a hint on the code it points at.
        val context = diagnostic.labels.single { !it.primary }
        assertEquals("Type superimposed over here", context.message)
        assertEquals(4, context.startCharacter)
        assertEquals(8, context.endCharacter)
    }

    @Test
    fun `reads a syntax error, which has one label and no note`() {
        val diagnostic = SppDiagnosticParser.parse(SYNTAX_ERROR).single()
        assertEquals("E0", diagnostic.code)
        assertEquals("Syntax Error", diagnostic.title)
        assertEquals(12, diagnostic.primaryLabel!!.startCharacter)
        assertEquals("", diagnostic.note)
    }

    @Test
    fun `ignores everything that is not a diagnostic`() {
        val noise = "Updated std from https://github.com/SamG101-Developer/SPP-STL (master)\n" +
            "\rAnalysing semantics  [####------] 40%\r\n" +
            SEMANTIC_ERROR
        assertEquals(1, SppDiagnosticParser.parse(noise).size)
        assertEquals(0, SppDiagnosticParser.parse("").size)
        assertNull(SppDiagnosticParser.parse("{not json at all}").firstOrNull())
    }

    @Test
    fun `marks a label with no place in the source as generated`() {
        val label = SppDiagnosticParser.parse(GENERATED_LABEL).single().primaryLabel!!
        assertTrue(label.generated)
    }

    @Test
    fun `reads what a name resolved to, and where it was declared`() {
        val symbols = SppDiagnosticParser.parseSymbols(SYMBOLS)
        assertEquals(2, symbols.size)

        // A type declared in the same file: the use is where it is written, the definition where the class is.
        val probe = symbols.single { it.name == "IndexProbe" }
        assertEquals("type", probe.kind)
        assertEquals(4, probe.use.startLine)
        assertEquals(18, probe.use.startCharacter)
        assertEquals(0, probe.definition.startLine)
        assertEquals(4, probe.definition.startCharacter)
        assertTrue(probe.definition.file.endsWith("zz_index_probe.spp"))

        // An imported type resolves through the import to the file that declares it, rather than to the prelude.
        val s32 = symbols.single { it.name == "S32" }
        assertTrue(s32.definition.file.endsWith("std/num/sized_integer.spp"))
        assertEquals(40, s32.definition.startLine)
    }

    @Test
    fun `reads what cfg left out, across every line it covers`() {
        val region = SppDiagnosticParser.parseInactive(INACTIVE).single()
        assertEquals(0, region.where.startLine)
        assertEquals(0, region.where.startCharacter)
        assertEquals(3, region.where.endLine)
        assertEquals(1, region.where.endCharacter)
        assertTrue(region.where.file.endsWith("zz_cfg_probe.spp"))
        assertEquals(0, SppDiagnosticParser.parseInactive(SEMANTIC_ERROR).size)
    }

    @Test
    fun `keeps diagnostics and symbols apart`() {
        val mixed = SEMANTIC_ERROR + "\n" + SYMBOLS
        assertEquals(1, SppDiagnosticParser.parse(mixed).size)
        assertEquals(2, SppDiagnosticParser.parseSymbols(mixed).size)
    }

    private companion object {
        const val SEMANTIC_ERROR =
            """{"kind":"diagnostic","severity":"error","code":"E111","title":"Superimposition External Marker Extension Error","labels":[{"primary":false,"message":"Type superimposed over here","file":"/home/sam/spp/project/src/testing/zz_idea_probe.spp","start":{"line":0,"character":4},"end":{"line":0,"character":8}},{"primary":true,"message":"Marker superimposed from outside its package here","file":"/home/sam/spp/project/src/testing/zz_idea_probe.spp","start":{"line":0,"character":13},"end":{"line":0,"character":17}}],"note":"A marker decides how every value of a type is handled, so only the package declaring the type may superimpose one over it.","help":"Superimpose the marker where the type is declared, or wrap the type in one this package owns.","wrapped":[]}"""

        const val SYNTAX_ERROR =
            """{"kind":"diagnostic","severity":"error","code":"E0","title":"Syntax Error","labels":[{"primary":true,"message":"Expected '&', '(', ')', '..', '[', 'mut', 'self', a character, got '-'","file":"/home/sam/spp/project/src/testing/zz_idea_probe.spp","start":{"line":0,"character":12},"end":{"line":0,"character":13}}],"note":"","help":"","wrapped":[]}"""

        val SYMBOLS = listOf(
            """{"kind":"symbol","symbol":"type","name":"IndexProbe","type":"","use":{"file":"/home/sam/spp/project/src/testing/zz_index_probe.spp","start":{"line":4,"character":18},"end":{"line":4,"character":28}},"definition":{"file":"/home/sam/spp/project/src/testing/zz_index_probe.spp","start":{"line":0,"character":4},"end":{"line":0,"character":14}}}""",
            """{"kind":"symbol","symbol":"type","name":"S32","type":"","use":{"file":"/home/sam/spp/project/src/testing/zz_index_probe.spp","start":{"line":4,"character":33},"end":{"line":4,"character":36}},"definition":{"file":"/home/sam/spp/project/vcs/std/src/std/num/sized_integer.spp","start":{"line":40,"character":4},"end":{"line":40,"character":16}}}""",
        ).joinToString("\n")

        const val INACTIVE =
            """{"kind":"inactive","where":{"file":"/home/sam/spp/project/src/testing/zz_cfg_probe.spp","start":{"line":0,"character":0},"end":{"line":3,"character":1}}}"""

        const val MEMBERS_WITH_VISIBILITY =
            """{"kind":"members","owner":"&testing::zz_probe_vis::ProbeVis","of":"type","members":[{"name":"open","member":"attribute","type":"S32","definition":{"file":"/home/sam/spp/project/src/testing/zz_probe_vis.spp","start":{"line":1,"character":10},"end":{"line":1,"character":14}},"visibility":"public","signatures":[]},{"name":"hidden","member":"attribute","type":"S32","definition":{"file":"/home/sam/spp/project/src/testing/zz_probe_vis.spp","start":{"line":3,"character":2},"end":{"line":3,"character":8}},"visibility":"private","signatures":[]}]}"""

        const val GENERATED_LABEL =
            """{"kind":"diagnostic","severity":"error","code":"E7","title":"Some Error","labels":[{"primary":true,"message":"in generated code","file":"/tmp/x.spp","generated":true}],"note":"","help":"","wrapped":[]}"""
    }
}
