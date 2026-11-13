package com.github.samg101developer.sppidea.run

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project

// Code analysis uses the selected S++ configuration's `spp`
// executable, so selecting another configuration (or editing
// the selected one) re-runs it with that executable.
class SppExecutableSelectionListener(private val project: Project) : RunManagerListener {
  override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) = restart()

  override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
    if (settings.configuration is SppRunProfile) restart()
  }

  private fun restart() {
    if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart("S++ executable selection changed")
  }
}
