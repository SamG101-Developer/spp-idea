package com.github.samg101developer.sppidea.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

// Editor UI for run configuration: the directory to look for
// modules in, the module (an S++ project in that directory),
// and optional program arguments passed after the "spp"
// subcommand.
class SppRunConfigurationEditor(private val project: Project) : SettingsEditor<SppRunProfile>() {
  private val modulesPathField = TextFieldWithBrowseButton()
  private val moduleModel = DefaultComboBoxModel<String>()
  private val moduleComboBox = ComboBox(moduleModel)
  private val argumentsField = JBTextField()

  init {
    modulesPathField.addBrowseFolderListener(
      project,
      FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Modules Directory"),
    )

    // Left blank, the default directory is used; show which.
    (modulesPathField.textField as? JBTextField)?.emptyText?.text =
      SppRunSupport.defaultModulesDirectory(project)?.toString() ?: ""

    // The modules on offer follow the directory as it is typed
    // or chosen.
    modulesPathField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = refreshModules()
    })
  }

  // The directory currently in the form, or the default one
  // if the field is blank.
  private fun modulesDirectory(): Path? =
    modulesPathField.text.takeIf { it.isNotBlank() }?.let { runCatching { Path.of(it.trim()) }.getOrNull() }
      ?: SppRunSupport.defaultModulesDirectory(project)

  // Re-list the modules in the directory, keeping the selected
  // one if it is still there. With nothing selected, a lone
  // module is picked, so a single-project directory is ready
  // to go.
  private fun refreshModules(keep: String? = moduleComboBox.selectedItem as? String) {
    val modules = SppRunSupport.findModules(modulesDirectory())
    moduleModel.removeAllElements()
    moduleModel.addAll(modules)
    moduleComboBox.selectedItem = keep?.takeIf { it in modules } ?: modules.singleOrNull()
  }

  // A configuration that has not chosen a module yet falls
  // back to the only module in its directory, so the form
  // opens already filled in and consistent with the name the
  // configuration was given.
  override fun resetEditorFrom(configuration: SppRunProfile) {
    modulesPathField.text = configuration.modulesPath ?: ""
    refreshModules(configuration.moduleName?.takeIf { it.isNotBlank() })
    argumentsField.text = configuration.programArguments ?: ""
  }

  // The configuration is updated with the directory, module and
  // program arguments chosen in the form. A blank directory is
  // stored as blank, so that it keeps following the default.
  override fun applyEditorTo(configuration: SppRunProfile) {
    configuration.modulesPath = modulesPathField.text.trim()
    configuration.moduleName = moduleComboBox.selectedItem as? String
    configuration.programArguments = argumentsField.text
  }

  // The editor UI is a simple form: the modules directory, the
  // module (an S++ project, i.e. a folder with "spp.toml", in
  // that directory), and the program arguments.
  override fun createEditor(): JComponent = panel {
    row("Modules directory:") {
      cell(modulesPathField).align(AlignX.FILL)
        .comment("Folder containing S++ projects. Leave blank for the project root's parent, so the root itself is listed.")
    }
    row("Module:") {
      cell(moduleComboBox).align(AlignX.FILL)
    }
    row("Program arguments:") {
      cell(argumentsField).align(AlignX.FILL)
    }
  }
}
