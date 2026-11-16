package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.run.saveAllDocumentsBeforeSpp
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Anything typed but not yet saved is written to disk before "spp" is started, so a build or run straight after
 * an edit sees the edit.
 */
class SppSaveBeforeRunTest : BasePlatformTestCase() {

    fun testUnsavedEditIsWrittenOut() {
        val file = myFixture.configureByText("main.spp", "fun f() -> Void {\n}\n")
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "# edited\n") }
        val documents = FileDocumentManager.getInstance()
        assertTrue(documents.isDocumentUnsaved(document))

        saveAllDocumentsBeforeSpp()

        assertFalse(documents.isDocumentUnsaved(document))
        assertEquals("# edited\nfun f() -> Void {\n}\n", VfsUtilCore.loadText(file.virtualFile))
    }
}
