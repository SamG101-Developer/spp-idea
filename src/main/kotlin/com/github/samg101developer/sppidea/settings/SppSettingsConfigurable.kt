package com.github.samg101developer.sppidea.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.io.File

// The page in the Settings dialog that allows the user to
// configure the S++ executable path, found in "Settings |
// Languages & Frameworks | S++".
class SppSettingsConfigurable : BoundConfigurable("S++") {

  // Creates the UI panel for the S++ settings, which consists
  // of a single row with a text field and a browse button for
  // selecting the S++ executable path.
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

  // When the user clicks "Apply" in the Settings dialog,
  // validate that the S++ executable path is not blank and
  // points to an existing executable file. If the validation
  // fails, throw a ConfigurationException to prevent the
  // settings from being applied.
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

// Returns the configured `spp` executable path, or `null` if
// it isn't configured or doesn't point at an executable file.
fun resolveSppExecutable(): String? {
  val path = SppSettings.getInstance().sppExecutablePath
  if (path.isBlank()) return null
  val file = File(path)
  return if (file.isFile && file.canExecute()) path else null
}
