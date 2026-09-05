package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.lang.SppIcons
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue

/**
 * The "S++" run configuration type, offering three factories that map 1:1 onto the `spp` CLI:
 * Build (`spp build`), Run (`spp run`) and Test (`spp test`, which builds and runs on its own).
 */
class SppConfigurationType : ConfigurationTypeBase(
    ID,
    "S++",
    "S++ build/run/test configuration",
    NotNullLazyValue.createValue { SppIcons.FILE },
) {
    init {
        addFactory(SppCommandConfigurationFactory(this, SppCommand.BUILD))
        addFactory(SppCommandConfigurationFactory(this, SppCommand.RUN))
        addFactory(SppCommandConfigurationFactory(this, SppCommand.TEST))
    }

    companion object {
        const val ID = "SppConfigurationType"
    }
}

/** One factory per [SppCommand], so Build/Run/Test each show up as their own creatable configuration kind. */
class SppCommandConfigurationFactory(
    type: SppConfigurationType,
    private val command: SppCommand,
) : ConfigurationFactory(type) {

    override fun getId(): String = command.name

    override fun getName(): String = command.displayName

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        SppRunConfiguration(project, this, command.displayName, command)

    override fun getOptionsClass(): Class<out BaseState> = SppRunConfigurationOptions::class.java
}
