package com.github.samg101developer.sppidea.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.io.File

/** Settings | Languages & Frameworks | S++ */
class SppSettingsConfigurable : BoundConfigurable("S++") {

    override fun createPanel() = panel {
        row("S++ executable:") {
            val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("Select S++ Executable")
                .withDescription("Select the `spp` compiler/toolchain binary used to build, run and test S++ projects.")
            textFieldWithBrowseButton(project = null, fileChooserDescriptor = descriptor)
                .bindText(SppSettings.getInstance()::sppExecutablePath)
                .align(AlignX.FILL)
        }
        row {
            comment("Used to run <code>spp build</code>, <code>spp run</code>, <code>spp test</code> and <code>spp init</code>.")
        }
    }

    override fun apply() {
        super.apply()
        val path = SppSettings.getInstance().sppExecutablePath
        if (path.isNotBlank() && !File(path).let { it.isFile && it.canExecute() }) {
            throw com.intellij.openapi.options.ConfigurationException(
                "The selected S++ executable does not exist or is not executable: $path"
            )
        }
    }
}

/** Returns the configured `spp` executable path, or `null` if it isn't configured or doesn't point at an executable file. */
fun resolveSppExecutable(): String? {
    val path = SppSettings.getInstance().sppExecutablePath
    if (path.isBlank()) return null
    val file = File(path)
    return if (file.isFile && file.canExecute()) path else null
}
