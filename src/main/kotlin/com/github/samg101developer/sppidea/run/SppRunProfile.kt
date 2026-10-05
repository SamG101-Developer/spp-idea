package com.github.samg101developer.sppidea.run

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager

// An S++ run configuration, whatever class implements it: the
// plain SppRunConfiguration, or the CLion one in the "clion"
// module, which has to extend CLion's own base class. The
// build action, the editor and the runner only use this.
interface SppRunProfile : RunConfiguration {
  val kind: SppConfigurationKind
  var moduleName: String?
  var programArguments: String?
}

// Creates the run configuration for an S++ factory. Without
// an extension this is SppRunConfiguration; CLion registers
// one that makes a configuration CLion treats as C++, so that
// selecting it does not change the C++ resolve configuration.
interface SppRunConfigurationProvider {
  fun create(project: Project, factory: ConfigurationFactory, name: String, kind: SppConfigurationKind): RunConfiguration

  companion object {
    val EP_NAME = ExtensionPointName<SppRunConfigurationProvider>(
      "com.github.samg101developer.sppidea.runConfigurationProvider"
    )
  }
}

// The behaviour every S++ run configuration shares, so that
// both classes run "spp" in exactly the same way.
object SppRunSupport {

  // The effective module name is the module name specified in
  // the configuration, or (if that is blank) the name of the
  // only module in the project, or null if there is no module
  // name and more than one module in the project.
  fun effectiveModuleName(profile: SppRunProfile): String? = profile.moduleName?.takeIf { it.isNotBlank() }
    ?: ModuleManager.getInstance(profile.project).modules.singleOrNull()?.name

  // The suggested name for the run configuration is the effective
  // module name plus the kind's name suffix, or null if there is
  // no effective module name.
  fun suggestedName(profile: SppRunProfile): String? = effectiveModuleName(profile)?.plus(profile.kind.nameSuffix)

  // Resolve the module for the run configuration, or null if
  // there is no effective module name or if the module cannot
  // be found.
  fun resolveModule(profile: SppRunProfile): Module? =
    effectiveModuleName(profile)?.let { ModuleManager.getInstance(profile.project).findModuleByName(it) }

  // The directory "spp" is invoked in: the module's content root,
  // falling back to the project root.
  fun resolveWorkingDirectory(profile: SppRunProfile): String? {
    val module = resolveModule(profile) ?: return null
    return ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path ?: profile.project.basePath
  }

  // Validate that the configuration is valid: the S++ executable
  // is configured and a module is selected.
  fun checkConfiguration(profile: SppRunProfile) {
    if (resolveSppExecutable() == null) {
      throw RuntimeConfigurationError(
        "The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++."
      )
    }
    if (resolveModule(profile) == null) {
      throw RuntimeConfigurationError("Select a module for this S++ configuration.")
    }
  }

  // Create the run profile state for the configuration, which is
  // used to start the process. The state is a command line state
  // that invokes the "spp" command with the appropriate arguments.
  fun createState(profile: SppRunProfile, environment: ExecutionEnvironment): CommandLineState {
    val sppPath = resolveSppExecutable()
      ?: throw ExecutionException("The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++.")
    val workingDir = resolveWorkingDirectory(profile)
      ?: throw ExecutionException("No module selected for this run configuration.")

    return object : CommandLineState(environment) {
      override fun startProcess(): ProcessHandler {
        val handler = startSppProcess(
          sppCommandLine(sppPath, profile.kind.runCommand, workingDir, profile.programArguments)
        )
        ProcessTerminatedListener.attach(handler)
        return handler
      }
    }
  }
}
