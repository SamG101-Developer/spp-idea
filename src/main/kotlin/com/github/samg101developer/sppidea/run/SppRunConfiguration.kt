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

/**
 * A run configuration that invokes `spp` for a chosen module. Running a source configuration
 * invokes `spp run` (which builds first) and running a test configuration invokes `spp test`;
 * `spp build` is not reached through here at all, but through [SppBuildLauncher], which reports
 * into the Build tool window instead of a run console.
 */
class SppRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
    val kind: SppConfigurationKind,
) : LocatableConfigurationBase<SppRunConfigurationOptions>(project, factory, name) {

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

    /**
     * Names a configuration after the module it acts on, so a fresh one reads "spp-stl" or
     * "spp-stl-test" rather than "Unnamed". The platform asks for this both when the configuration
     * is created and, for as long as the name is still a generated one, after every edit to the
     * form -- so picking a different module in the editor renames the configuration to match.
     *
     * A project with exactly one module answers for a configuration that has not chosen one yet,
     * which is the case at creation time; with several modules there is nothing to guess, and the
     * name stays "Unnamed" until the editor picks one.
     */
    override fun suggestedName(): String? = effectiveModuleName()?.plus(kind.nameSuffix)

    /**
     * The module this configuration acts on. A project with exactly one module answers for a
     * configuration that has not chosen one, so the name, the editor and the launch all agree on
     * the same module however the configuration was created.
     */
    private fun effectiveModuleName(): String? = moduleName?.takeIf { it.isNotBlank() }
        ?: ModuleManager.getInstance(project).modules.singleOrNull()?.name

    fun resolveModule(): Module? =
        effectiveModuleName()?.let { ModuleManager.getInstance(project).findModuleByName(it) }

    /** The directory `spp` is invoked in: the module's content root, falling back to the project root. */
    fun resolveWorkingDirectory(): String? {
        val module = resolveModule() ?: return null
        return ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path ?: project.basePath
    }

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
