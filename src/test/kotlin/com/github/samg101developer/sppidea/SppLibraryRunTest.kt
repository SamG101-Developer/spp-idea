package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.run.SppRunSupport
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

/**
 * A library ('build = "lib"' in spp.toml) has nothing to run, so an S++ run configuration for one is invalid before it
 * is started. Its test configuration is not.
 */
class SppLibraryRunTest : BasePlatformTestCase() {

    fun testBuildTypeIsReadFromTheProjectTable() {
        assertEquals("lib", SppRunSupport.buildType(listOf("[project]", "name = \"std\"", "build = \"lib\"")))
        assertEquals("exe", SppRunSupport.buildType(listOf("[project]", "build = 'exe'  # comment")))
        assertNull(SppRunSupport.buildType(listOf("[project]", "name = \"x\"", "[other]", "build = \"lib\"")))
        assertNull(SppRunSupport.buildType(listOf("[vcs]", "build = \"lib\"")))
    }

    fun testLibraryDirectoryIsDetected() {
        val dir = Files.createTempDirectory("spp-lib")
        Files.writeString(dir.resolve("spp.toml"), "[project]\nname = \"a\"\nbuild = \"lib\"\n")
        assertTrue(SppRunSupport.isLibrary(dir))
        Files.writeString(dir.resolve("spp.toml"), "[project]\nname = \"a\"\nbuild = \"exe\"\n")
        assertFalse(SppRunSupport.isLibrary(dir))
        assertFalse(SppRunSupport.isLibrary(dir.resolve("missing")))
    }
}
