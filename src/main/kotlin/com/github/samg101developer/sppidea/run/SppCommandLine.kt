package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.util.execution.ParametersListUtil

// Build the command line for one "spp" executable invocation,
// shared by the run config and the build action.
fun sppCommandLine(
  sppPath: String,
  command: SppCommand,
  workingDir: String,
  arguments: String?,
): GeneralCommandLine {
  val commandLine = PtyCommandLine(GeneralCommandLine(sppPath))
    .withConsoleMode(false)
    .withInitialColumns(200)
    .withParameters(command.cliArg)
    .withWorkDirectory(workingDir)
  arguments?.takeIf { it.isNotBlank() }?.let { commandLine.addParameters(ParametersListUtil.parse(it)) }
  return commandLine
}

// Start the command line with a handler that decodes ANSI
// escapes, allowing the terminal to display colour outputs,
// not raw ANSI escape bytes.
fun startSppProcess(commandLine: GeneralCommandLine): KillableColoredProcessHandler =
  KillableColoredProcessHandler(commandLine)
