package com.github.samg101developer.sppidea.run

import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/** Editor UI for [SppRunConfiguration]: module + optional program arguments passed after the `spp` subcommand. */
class SppRunConfigurationEditor(private val project: Project) : SettingsEditor<SppRunConfiguration>() {

    private val moduleComboBox = ModulesComboBox().apply { fillModules(project) }
    private val argumentsField = JBTextField()

    override fun resetEditorFrom(configuration: SppRunConfiguration) {
        // A configuration that has not chosen a module yet falls back to the project's only
        // module, so a single-module S++ project opens the form already filled in and consistent
        // with the name the configuration was given.
        val moduleManager = ModuleManager.getInstance(project)
        moduleComboBox.selectedModule = configuration.moduleName
            ?.takeIf { it.isNotBlank() }
            ?.let { moduleManager.findModuleByName(it) }
            ?: moduleManager.modules.singleOrNull()
        argumentsField.text = configuration.programArguments ?: ""
    }

    override fun applyEditorTo(configuration: SppRunConfiguration) {
        configuration.moduleName = moduleComboBox.selectedModule?.name
        configuration.programArguments = argumentsField.text
    }

    override fun createEditor(): JComponent = panel {
        row("Module:") {
            cell(moduleComboBox).align(AlignX.FILL)
        }
        row("Program arguments:") {
            cell(argumentsField).align(AlignX.FILL)
        }
    }
}
