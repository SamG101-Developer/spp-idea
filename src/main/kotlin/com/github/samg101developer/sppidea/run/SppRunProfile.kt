package com.github.samg101developer.sppidea.run

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.github.samg101developer.sppidea.settings.sppExecutableMissingMessage
import com.intellij.execution.RunManager
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.TargetAwareRunProfile
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
import kotlin.io.path.readLines

// An S++ run configuration, whatever class implements it: the
// plain SppRunConfiguration, or the CLion one in the "clion"
// module, which has to extend CLion's own base class. The
// build action, the editor and the runner only use this. Its
// execution targets are the build modes (SppModeTarget).
interface SppRunProfile : RunConfiguration, TargetAwareRunProfile {
  val kind: SppConfigurationKind
  var executableName: String?
  var modulesPath: String?
  var moduleName: String?
  var programArguments: String?

  override fun canRunOn(target: ExecutionTarget): Boolean = target is SppModeTarget && target.canRun(this)
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

  // The `spp` executable for a project: the one picked by the
  // selected S++ run configuration, or the default one when the
  // selected configuration is not an S++ one. Used by code
  // analysis, so switching configuration switches toolchain.
  fun executableFor(project: Project): String? {
    val selected = RunManager.getInstanceIfCreated(project)?.selectedConfiguration?.configuration as? SppRunProfile
    return resolveSppExecutable(selected?.executableName)
  }

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

  // Whether the project in "directory" is a library: its spp.toml
  // has 'build = "lib"' in its [project] table. An unreadable or
  // missing file counts as not, leaving "spp" to report it.
  fun isLibrary(directory: Path): Boolean {
    val lines = runCatching { directory.resolve(PROJECT_FILE).readLines() }.getOrNull() ?: return false
    return buildType(lines) == "lib"
  }

  // The "build" value of the [project] table in spp.toml's lines,
  // or null when there is none.
  internal fun buildType(lines: List<String>): String? {
    var inProject = false
    for (raw in lines) {
      val line = raw.substringBefore('#').trim()
      if (line.startsWith("[")) {
        inProject = line == "[project]"
        continue
      }
      if (!inProject) continue
      val match = BUILD_LINE.matchEntire(line) ?: continue
      return match.groupValues[1]
    }
    return null
  }

  private val BUILD_LINE = Regex("""build\s*=\s*["']([^"']*)["']""")

  // Validate that the configuration is valid: the S++ executable
  // chosen is configured, a module is selected, and a run configuration's
  // module is not a library, which has nothing to run.
  fun checkConfiguration(profile: SppRunProfile) {
    if (resolveSppExecutable(profile.executableName) == null) {
      throw RuntimeConfigurationError(sppExecutableMissingMessage(profile.executableName))
    }
    val workingDir = resolveWorkingDirectory(profile)
      ?: throw RuntimeConfigurationError("Select a module for this S++ configuration.")
    if (profile.kind.runCommand == SppCommand.RUN && isLibrary(Path.of(workingDir))) {
      throw RuntimeConfigurationError(
        "This module is a library ('build = \"lib\"' in spp.toml), so there is nothing to run. " +
          "Use Build to build it, or an S++ Test configuration to run its tests."
      )
    }
  }

  // Create the run profile state for the configuration, which is
  // used to start the process. The state is a command line state
  // that invokes the "spp" command with the appropriate arguments.
  fun createState(profile: SppRunProfile, environment: ExecutionEnvironment): CommandLineState {
    val sppPath = resolveSppExecutable(profile.executableName)
      ?: throw ExecutionException(sppExecutableMissingMessage(profile.executableName))
    val workingDir = resolveWorkingDirectory(profile)
      ?: throw ExecutionException("No module selected for this run configuration.")

    val mode = SppModeTarget.modeFor(profile, environment.executionTarget)

    return object : CommandLineState(environment) {
      override fun startProcess(): ProcessHandler {
        val handler = startSppProcess(
          sppCommandLine(sppPath, profile.kind.runCommand, workingDir, profile.programArguments, mode)
        )
        ProcessTerminatedListener.attach(handler)
        return handler
      }
    }
  }
}
