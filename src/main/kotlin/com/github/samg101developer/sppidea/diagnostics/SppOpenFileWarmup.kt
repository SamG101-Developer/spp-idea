package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/**
 * Starts working out what the names in a file mean as soon as it is opened or switched to, rather than waiting for
 * the file to be highlighted. Navigation can only use what is already known, so this is what makes a Ctrl+click on a
 * freshly opened file land the first time rather than the third.
 */
class SppOpenFileWarmup : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.extension != "spp") return
        SppCompilerDiagnostics.getInstance(source.project).warmUp(file)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (file.extension != "spp") return
        SppCompilerDiagnostics.getInstance(event.manager.project).warmUp(file)
    }
}
