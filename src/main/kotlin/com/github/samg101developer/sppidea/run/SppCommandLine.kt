package com.github.samg101developer.sppidea.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.execution.ParametersListUtil

// Build the command line for one "spp" executable invocation,
// shared by the run config, the build action and the vcs
// action (which has no mode, so passes null). A run's
// arguments are the program's, so they follow "--", which
// "spp run" passes on to it; a test's are "spp test"'s own.
fun sppCommandLine(
  sppPath: String,
  command: SppCommand,
  workingDir: String,
  arguments: String?,
  mode: SppBuildMode?,
): GeneralCommandLine {
  val commandLine = PtyCommandLine(GeneralCommandLine(sppPath))
    .withConsoleMode(false)
    .withInitialColumns(200)
    .withParameters(command.cliArg)
    .withWorkDirectory(workingDir)
  mode?.let { commandLine.addParameters("--mode", it.cliArg) }
  arguments?.takeIf { it.isNotBlank() }?.let {
    if (command == SppCommand.RUN) commandLine.addParameter("--")
    commandLine.addParameters(ParametersListUtil.parse(it))
  }
  return commandLine
}

// Start the command line with a handler that decodes ANSI
// escapes, allowing the terminal to display colour outputs,
// not raw ANSI escape bytes.
fun startSppProcess(commandLine: GeneralCommandLine): KillableColoredProcessHandler =
  KillableColoredProcessHandler(commandLine)

// "spp" reads the sources from disk, so anything typed but not yet
// saved has to be written out before it starts, or it builds the
// previous version. Waits for the save when called off the UI
// thread, unless inside a read action, where waiting on the UI
// thread could deadlock (the save is then only scheduled).
fun saveAllDocumentsBeforeSpp() {
  val app = ApplicationManager.getApplication()
  val save = Runnable { FileDocumentManager.getInstance().saveAllDocuments() }
  when {
    app.isDispatchThread -> save.run()
    app.isReadAccessAllowed -> app.invokeLater(save)
    else -> app.invokeAndWait(save)
  }
}
