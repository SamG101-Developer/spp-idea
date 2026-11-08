package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.module.SppReadOnlyFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Which S++ files are locked against accidental edits: only a project's own "src", "tst" and "ffi" are editable, and
 * nothing under "vcs" is, even when a dependency there has its own "spp.toml".
 */
class SppReadOnlyFilesTest {
    private val projects = mutableSetOf(Path.of("/work/app"))

    private fun locked(file: String) = SppReadOnlyFiles.isLocked(Path.of(file)) { it in projects }

    @Test
    fun `own source, test and ffi files are editable`() {
        assertFalse(locked("/work/app/src/main.spp"))
        assertFalse(locked("/work/app/src/deep/nested/mod.spp"))
        assertFalse(locked("/work/app/tst/main.spp"))
        assertFalse(locked("/work/app/ffi/libc/stub.spp"))
    }

    @Test
    fun `dependency files are locked`() {
        assertTrue(locked("/work/app/vcs/std/src/std/io.spp"))
        assertTrue(locked("/work/app/vcs/std/ffi/sppc/stub.spp"))
    }

    @Test
    fun `dependency files are locked when the dependency has its own manifest`() {
        projects.add(Path.of("/work/app/vcs/std"))
        assertTrue(locked("/work/app/vcs/std/src/std/io.spp"))
        assertTrue(locked("/work/app/vcs/std/ffi/sppc/stub.spp"))
    }

    @Test
    fun `files elsewhere in the project are locked`() {
        assertTrue(locked("/work/app/out/generated.spp"))
        assertTrue(locked("/work/app/loose.spp"))
    }

    @Test
    fun `files outside any project are left alone`() {
        assertFalse(locked("/elsewhere/scratch.spp"))
    }
}
