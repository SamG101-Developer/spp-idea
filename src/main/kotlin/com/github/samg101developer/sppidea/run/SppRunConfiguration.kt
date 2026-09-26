package com.github.samg101developer.sppidea.run

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager

// The fun configuration that invokes the "spp" command line
// for a chosen module. The kind of configuration (run/test)
// is fixed at creation time.
class SppRunConfiguration(
  project: Project,
  factory: ConfigurationFactory,
  name: String,
  val kind: SppConfigurationKind,
) : LocatableConfigurationBase<SppRunConfigurationOptions>(project, factory, name) {

  // The options class for the S++ run configuration, which
  // stores the configuration's state (module name and program
  // arguments).
  public override fun getOptions(): SppRunConfigurationOptions =
    super.getOptions() as SppRunConfigurationOptions

  var moduleName: String?
    get() = options.moduleName
    set(value) {
      options.moduleName = value
    }

  var programArguments: String?
    get() = options.programArguments
    set(value) {
      options.programArguments = value
    }

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
    SppRunConfigurationEditor(project)

  // The suggested name for the run configuration is the effective
  // module name plus the kind's name suffix, or null if there is
  // no effective module name.
  override fun suggestedName(): String? = effectiveModuleName()?.plus(kind.nameSuffix)

  // The effective module name is the module name specified in
  // the configuration, or (if that is blank) the name of the
  // only module in the project, or null if there is no module
  // name and more than one module in the project.
  private fun effectiveModuleName(): String? = moduleName?.takeIf { it.isNotBlank() }
    ?: ModuleManager.getInstance(project).modules.singleOrNull()?.name

  // Resolve the module for the run configuration, or null if
  // there is no effective module name or if the module cannot
  // be found.
  fun resolveModule(): Module? =
    effectiveModuleName()?.let { ModuleManager.getInstance(project).findModuleByName(it) }

  // The directory "spp" is invoked in: the module's content root,
  // falling back to the project root.
  fun resolveWorkingDirectory(): String? {
    val module = resolveModule() ?: return null
    return ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path ?: project.basePath
  }

  // Validate that the configuration is valid: the S++ executable
  // is configured and a module is selected.
  override fun checkConfiguration() {
    if (resolveSppExecutable() == null) {
      throw RuntimeConfigurationError(
        "The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++."
      )
    }
    if (resolveModule() == null) {
      throw RuntimeConfigurationError("Select a module for this S++ configuration.")
    }
  }

  // Create the run profile state for the configuration, which is
  // used to start the process. The state is a command line state
  // that invokes the "spp" command with the appropriate arguments.
  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
    val sppPath = resolveSppExecutable()
      ?: throw ExecutionException("The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++.")
    val workingDir = resolveWorkingDirectory()
      ?: throw ExecutionException("No module selected for this run configuration.")

    return object : CommandLineState(environment) {
      override fun startProcess(): ProcessHandler {
        val handler = startSppProcess(
          sppCommandLine(sppPath, kind.runCommand, workingDir, programArguments)
        )
        ProcessTerminatedListener.attach(handler)
        return handler
      }
    }
  }
}
