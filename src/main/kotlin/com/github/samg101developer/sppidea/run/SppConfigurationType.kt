package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.lang.SppIcons
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue

/**
 * The "S++" run configuration type. A source configuration can be built (`spp build`, via the
 * Build action) or run (`spp run`); a test configuration can only be run (`spp test`).
 */
class SppConfigurationType : ConfigurationTypeBase(
    ID,
    "S++",
    "S++ build/run/test configuration",
    NotNullLazyValue.createValue { SppIcons.FILE },
) {
    init {
        SppConfigurationKind.entries.forEach { addFactory(SppKindConfigurationFactory(this, it)) }
    }

    companion object {
        const val ID = "SppConfigurationType"
    }
}

/** One factory per [SppConfigurationKind], so Source and Test are separately creatable. */
class SppKindConfigurationFactory(
    type: SppConfigurationType,
    private val kind: SppConfigurationKind,
) : ConfigurationFactory(type) {

    override fun getId(): String = kind.factoryId

    override fun getName(): String = kind.displayName

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        SppRunConfiguration(project, this, kind.displayName, kind)

    override fun getOptionsClass(): Class<out BaseState> = SppRunConfigurationOptions::class.java
}
