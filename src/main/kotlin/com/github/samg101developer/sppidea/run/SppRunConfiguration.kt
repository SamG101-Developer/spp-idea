package com.github.samg101developer.sppidea.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

// The fun configuration that invokes the "spp" command line
// for a chosen module. The kind of configuration (run/test)
// is fixed at creation time. Everything it does is shared
// with the CLion variant, through SppRunSupport.
class SppRunConfiguration(
  project: Project,
  factory: ConfigurationFactory,
  name: String,
  override val kind: SppConfigurationKind,
) : LocatableConfigurationBase<SppRunConfigurationOptions>(project, factory, name), SppRunProfile {

  // The options class for the S++ run configuration, which
  // stores the configuration's state (module name and program
  // arguments).
  public override fun getOptions(): SppRunConfigurationOptions =
    super.getOptions() as SppRunConfigurationOptions

  override var modulesPath: String?
    get() = options.modulesPath
    set(value) {
      options.modulesPath = value
    }

  override var moduleName: String?
    get() = options.moduleName
    set(value) {
      options.moduleName = value
    }

  override var programArguments: String?
    get() = options.programArguments
    set(value) {
      options.programArguments = value
    }

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
    SppRunConfigurationEditor(project)

  override fun suggestedName(): String? = SppRunSupport.suggestedName(this)

  override fun checkConfiguration() = SppRunSupport.checkConfiguration(this)

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    SppRunSupport.createState(this, environment)
}
