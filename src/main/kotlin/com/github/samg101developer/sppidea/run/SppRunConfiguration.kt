package com.github.samg101developer.sppidea.run

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.execution.ParametersListUtil

/** A run configuration that invokes `spp <build|run|test>` for a chosen module. */
class SppRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
    val command: SppCommand,
) : RunConfigurationBase<SppRunConfigurationOptions>(project, factory, name) {

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

    private fun resolveModule() = moduleName?.let { ModuleManager.getInstance(project).findModuleByName(it) }

    override fun checkConfiguration() {
        if (resolveSppExecutable() == null) {
            throw RuntimeConfigurationError(
                "The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++."
            )
        }
        if (resolveModule() == null) {
            throw RuntimeConfigurationError("Select a module to run 'spp ${command.cliArg}' on.")
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val sppPath = resolveSppExecutable()
            ?: throw ExecutionException("The S++ executable is not configured. Set it in Settings | Languages & Frameworks | S++.")
        val module = resolveModule()
            ?: throw ExecutionException("No module selected for this run configuration.")
        val workingDir = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path
            ?: project.basePath
            ?: throw ExecutionException("Could not determine a working directory for module '${module.name}'.")

        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val commandLine = GeneralCommandLine(sppPath)
                    .withParameters(command.cliArg)
                    .withWorkDirectory(workingDir)
                programArguments?.takeIf { it.isNotBlank() }?.let {
                    commandLine.addParameters(ParametersListUtil.parse(it))
                }
                val handler = KillableColoredProcessHandler(commandLine)
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }
}
