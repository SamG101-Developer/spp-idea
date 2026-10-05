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
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

// An S++ run configuration, whatever class implements it: the
// plain SppRunConfiguration, or the CLion one in the "clion"
// module, which has to extend CLion's own base class. The
// build action, the editor and the runner only use this.
interface SppRunProfile : RunConfiguration {
  val kind: SppConfigurationKind
  var modulesPath: String?
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

  // The file that marks a directory as an S++ project.
  const val PROJECT_FILE = "spp.toml"

  // The directory searched for modules when none is set: the
  // parent of the project root, so that the root itself is
  // offered as a module.
  fun defaultModulesDirectory(project: Project): Path? =
    project.basePath?.let { Path.of(it).parent }

  // The directory searched for modules: the one specified in
  // the configuration, or (if that is blank) the default.
  fun modulesDirectory(profile: SppRunProfile): Path? =
    profile.modulesPath?.takeIf { it.isNotBlank() }?.let { Path.of(it.trim()) }
      ?: defaultModulesDirectory(profile.project)

  // The modules in a directory: its sub-directories that are S++
  // projects, by name. An unreadable or missing directory has
  // none.
  fun findModules(directory: Path?): List<String> {
    if (directory == null || !directory.isDirectory()) return emptyList()
    return runCatching {
      directory.listDirectoryEntries()
        .filter { it.resolve(PROJECT_FILE).isRegularFile() }
        .map { it.fileName.toString() }
        .sorted()
    }.getOrDefault(emptyList())
  }

  // The effective module name is the module name specified in
  // the configuration, or (if that is blank) the only module in
  // the modules directory, or null if there is no module name
  // and not exactly one module to fall back to.
  fun effectiveModuleName(profile: SppRunProfile): String? = profile.moduleName?.takeIf { it.isNotBlank() }
    ?: findModules(modulesDirectory(profile)).singleOrNull()

  // The suggested name for the run configuration is the effective
  // module name plus the kind's name suffix, or null if there is
  // no effective module name.
  fun suggestedName(profile: SppRunProfile): String? = effectiveModuleName(profile)?.plus(profile.kind.nameSuffix)

  // The directory "spp" is invoked in: the module's directory
  // inside the modules directory, or null if it does not exist.
  fun resolveWorkingDirectory(profile: SppRunProfile): String? {
    val name = effectiveModuleName(profile) ?: return null
    val directory = modulesDirectory(profile)?.resolve(name) ?: return null
    return directory.takeIf { it.isDirectory() }?.toString()
  }

  // Validate that the configuration is valid: the S++ executable
  // is configured and a module is selected.
  fun checkConfiguration(profile: SppRunProfile) {
    if (resolveSppExecutable() == null) {
      throw RuntimeConfigurationError(
        "The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++."
      )
    }
    if (resolveWorkingDirectory(profile) == null) {
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
