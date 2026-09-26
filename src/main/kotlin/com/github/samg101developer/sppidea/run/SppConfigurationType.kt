package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.lang.SppIcons
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue

// The S++ run configuration type, which is used to create
// S++ run configurations for building, running, and testing.
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

// The S++ run configuration factory, which is used to create
// S++ run configurations for a specific kind of S++ run
// configuration.
class SppKindConfigurationFactory(
  type: SppConfigurationType,
  private val kind: SppConfigurationKind,
) : ConfigurationFactory(type) {

  // The factory ID is the same as the kind's factory ID, so
  // that the run configuration can be identified by its kind.
  override fun getId(): String = kind.factoryId

  // The name of the run configuration is the same as the kind's
  // display name, so that the run configuration can be identified
  // by its kind.
  override fun getName(): String = kind.displayName

  // Create a new S++ run configuration for the given project.
  override fun createTemplateConfiguration(project: Project): RunConfiguration =
    SppRunConfiguration(project, this, kind.displayName, kind)

  // Return the options class for the S++ run configuration, which
  // is used to store the configuration's state.
  override fun getOptionsClass(): Class<out BaseState> = SppRunConfigurationOptions::class.java
}
