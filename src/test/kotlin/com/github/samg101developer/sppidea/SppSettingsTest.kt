package com.github.samg101developer.sppidea

import com.github.samg101developer.sppidea.settings.SppExecutable
import com.github.samg101developer.sppidea.settings.SppSettings
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Several named `spp` executables, one of them the default, and the single path stored by earlier versions.
 */
class SppSettingsTest {

    @Test
    fun `a path from before named executables becomes the default`() {
        val settings = SppSettings()
        settings.loadState(SppSettings.State().apply { sppExecutablePath = "/opt/spp" })
        assertEquals(listOf(SppExecutable("Default", "/opt/spp")), settings.executables)
        assertEquals("/opt/spp", settings.find(null)?.path)
        assertEquals("", settings.state.sppExecutablePath)
    }

    @Test
    fun `a blank name is the default, and an unknown name is nothing`() {
        val settings = SppSettings()
        settings.executables = listOf(SppExecutable("Windows", "C:/spp.exe"), SppExecutable("WSL", "//wsl/spp"))
        assertEquals("Windows", settings.find("")?.name)
        settings.defaultExecutable = "WSL"
        assertEquals("WSL", settings.find(null)?.name)
        assertEquals("Windows", settings.find("Windows")?.name)
        assertNull(settings.find("Gone"))
    }

    @Test
    fun `the executables survive being saved and loaded`() {
        val state = SppSettings.State().apply {
            executables = mutableListOf(SppExecutable("Windows", "C:/spp.exe"), SppExecutable("WSL", "//wsl/spp"))
            defaultExecutable = "WSL"
        }
        val loaded = XmlSerializer.deserialize(XmlSerializer.serialize(state), SppSettings.State::class.java)
        val settings = SppSettings().apply { loadState(loaded) }
        assertEquals(state.executables, settings.executables)
        assertEquals("//wsl/spp", settings.find(null)?.path)
    }
}
