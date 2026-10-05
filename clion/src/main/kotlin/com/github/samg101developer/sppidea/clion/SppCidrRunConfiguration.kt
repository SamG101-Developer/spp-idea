package com.github.samg101developer.sppidea.clion

import com.github.samg101developer.sppidea.run.SppConfigurationKind
import com.github.samg101developer.sppidea.run.SppRunConfigurationEditor
import com.github.samg101developer.sppidea.run.SppRunConfigurationOptions
import com.github.samg101developer.sppidea.run.SppRunConfigurationProvider
import com.github.samg101developer.sppidea.run.SppRunProfile
import com.github.samg101developer.sppidea.run.SppRunSupport
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.CidrBuildConfiguration
import com.jetbrains.cidr.execution.CidrBuildConfigurationHelper
import com.jetbrains.cidr.execution.CidrBuildTarget
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration
import com.jetbrains.cidr.lang.workspace.OCRunConfiguration
import com.jetbrains.cidr.lang.workspace.OCWorkspaceRunConfigurationListener
import javax.swing.Icon

// The S++ run configuration as CLion sees it. CLion works out
// the C++ resolve configuration (the code-insight context) from
// the selected run configuration, and any configuration that is
// not an OCRunConfiguration gets none at all. So selecting a
// plain S++ configuration dropped the context, and selecting a
// CMake one again brought it back, each time re-syncing the
// whole C++ project. This one is an OCRunConfiguration whose
// resolve configuration is whatever is selected already, so
// selecting it changes nothing.
//
// Everything else is the same as SppRunConfiguration: the same
// options, editor and command line, through SppRunSupport.
class SppCidrRunConfiguration(
  project: Project,
  factory: ConfigurationFactory,
  name: String,
  override val kind: SppConfigurationKind,
) : OCRunConfiguration<SppCidrRunConfiguration.NoBuildConfiguration, SppCidrRunConfiguration.NoBuildTarget>(project, factory, name),
  SppRunProfile,
  RunConfigurationWithSuppressedDefaultDebugAction {

  // The factory's options class, SppRunConfigurationOptions, so
  // the module and arguments are stored exactly as in other IDEs.
  private val sppOptions: SppRunConfigurationOptions
    get() = options as SppRunConfigurationOptions

  override var modulesPath: String?
    get() = sppOptions.modulesPath
    set(value) {
      sppOptions.modulesPath = value
    }

  override var moduleName: String?
    get() = sppOptions.moduleName
    set(value) {
      sppOptions.moduleName = value
    }

  override var programArguments: String?
    get() = sppOptions.programArguments
    set(value) {
      sppOptions.programArguments = value
    }

  // Keep the current C++ context, whichever profile it is from.
  // CLion recomputes the selected resolve configuration when this
  // configuration is selected, gets back the same one, and so
  // does not re-sync.
  override fun getResolveConfiguration(target: ExecutionTarget): OCResolveConfiguration? =
    OCWorkspaceRunConfigurationListener.getSelectedResolveConfiguration(project)

  // S++ configurations build nothing with CLion's toolchain, so
  // there are no CLion build targets.
  override fun getHelper(): CidrBuildConfigurationHelper<NoBuildConfiguration, NoBuildTarget> = NoBuildHelper

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
    SppRunConfigurationEditor(project)

  // CLion names its configurations after the build target; S++
  // ones are named after the module, as in other IDEs.
  override fun suggestedName(): String? = SppRunSupport.suggestedName(this)

  override fun checkConfiguration() = SppRunSupport.checkConfiguration(this)

  override fun getState(executor: Executor, environment: ExecutionEnvironment): CommandLineState =
    SppRunSupport.createState(this, environment)

  // The build configuration and target types CLion's base class
  // asks for. There are never any of them.
  interface NoBuildConfiguration : CidrBuildConfiguration
  interface NoBuildTarget : CidrBuildTarget<NoBuildConfiguration>

  private object NoBuildHelper : CidrBuildConfigurationHelper<NoBuildConfiguration, NoBuildTarget>() {
    override fun getTargets(): List<NoBuildTarget> = emptyList()
  }

  // Registered in spp-clion.xml, so that S++ factories create
  // this class when running in CLion.
  class Provider : SppRunConfigurationProvider {
    override fun create(project: Project, factory: ConfigurationFactory, name: String, kind: SppConfigurationKind): RunConfiguration =
      SppCidrRunConfiguration(project, factory, name, kind)
  }
}
