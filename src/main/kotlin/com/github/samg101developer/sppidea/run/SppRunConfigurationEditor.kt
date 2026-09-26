package com.github.samg101developer.sppidea.run

import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

// Editor UI for run configuration: module + optional program
// arguments passed after the "spp" subcommand.
class SppRunConfigurationEditor(private val project: Project) : SettingsEditor<SppRunConfiguration>() {
  private val moduleComboBox = ModulesComboBox().apply { fillModules(project) }
  private val argumentsField = JBTextField()

  // A configuration that has not chosen a module yet falls
  // back to the project's only module, so a single-module
  // S++ project opens the form already filled in and consistent
  // with the name the configuration was given.
  override fun resetEditorFrom(configuration: SppRunConfiguration) {
    val moduleManager = ModuleManager.getInstance(project)
    moduleComboBox.selectedModule = configuration.moduleName
      ?.takeIf { it.isNotBlank() }
      ?.let { moduleManager.findModuleByName(it) }
      ?: moduleManager.modules.singleOrNull()
    argumentsField.text = configuration.programArguments ?: ""
  }

  // The configuration is updated with the module and program
  // arguments chosen in the form. The module is stored by name,
  // so that the configuration can be serialized and deserialized
  // without holding a reference to the module object.
  override fun applyEditorTo(configuration: SppRunConfiguration) {
    configuration.moduleName = moduleComboBox.selectedModule?.name
    configuration.programArguments = argumentsField.text
  }

  // The editor UI is a simple form with two rows: module and
  // program arguments. The module row is a combo box that lists
  // all modules in the project, and the program arguments row
  // is a text field that allows the user to enter any arguments.
  override fun createEditor(): JComponent = panel {
    row("Module:") {
      cell(moduleComboBox).align(AlignX.FILL)
    }
    row("Program arguments:") {
      cell(argumentsField).align(AlignX.FILL)
    }
  }
}
