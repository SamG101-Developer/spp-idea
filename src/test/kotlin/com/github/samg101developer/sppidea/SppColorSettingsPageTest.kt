package com.github.samg101developer.sppidea

import com.intellij.lang.SppColorSettingsPage
import com.intellij.lang.SppSyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour settings page's preview. The platform rejects a demo that uses a tag the page does not declare, and a
 * colour with no tag in the demo cannot be seen while it is being chosen, so both are checked here rather than found
 * by opening the settings.
 */
class SppColorSettingsPageTest {
    private val page = SppColorSettingsPage()
    private val tagPattern = Regex("""<(/?)([a-z_]+)>""")

    @Test
    fun `every tag in the demo is declared and balanced`() {
        val declared = page.additionalHighlightingTagToDescriptorMap.keys
        val open = ArrayDeque<String>()
        for (match in tagPattern.findAll(page.demoText)) {
            val (closing, name) = match.destructured
            assertTrue("undeclared tag <$name>", name in declared)
            if (closing.isEmpty()) open.addLast(name)
            else assertEquals("mismatched </$name>", name, open.removeLastOrNull())
        }
        assertTrue("unclosed tags: $open", open.isEmpty())
    }

    @Test
    fun `every colour the annotators add is shown in the demo`() {
        val used = tagPattern.findAll(page.demoText).map { it.groupValues[2] }.toSet()
        val tags = page.additionalHighlightingTagToDescriptorMap
        for ((tag, key) in tags) assertTrue("tag <$tag> (${key.externalName}) is never used", tag in used)
    }

    @Test
    fun `every finer key falls back to the one it replaced`() {
        val fallbacks = mapOf(
            SppSyntaxHighlighter.METHOD_CALL to SppSyntaxHighlighter.ATTRIBUTE,
            SppSyntaxHighlighter.MEMBER_ACCESS to SppSyntaxHighlighter.ATTRIBUTE,
            SppSyntaxHighlighter.FUNCTION_DECLARATION to SppSyntaxHighlighter.ATTRIBUTE,
            SppSyntaxHighlighter.COROUTINE_DECLARATION to SppSyntaxHighlighter.FUNCTION_DECLARATION,
            SppSyntaxHighlighter.NAMESPACE to SppSyntaxHighlighter.TYPE_IDENTIFIER,
            SppSyntaxHighlighter.NAMESPACED_CALL to SppSyntaxHighlighter.FUNCTION_CALL,
            SppSyntaxHighlighter.CHAR to SppSyntaxHighlighter.STRING,
            SppSyntaxHighlighter.FORMAT_PLACEHOLDER to SppSyntaxHighlighter.VALID_ESCAPE,
            SppSyntaxHighlighter.PARENTHESES to SppSyntaxHighlighter.BRACKET,
            SppSyntaxHighlighter.INACTIVE_CODE to SppSyntaxHighlighter.UNREACHED_CODE,
        )
        for ((key, parent) in fallbacks) assertEquals(key.externalName, parent, key.fallbackAttributeKey)
    }
}
