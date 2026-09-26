package com.github.samg101developer.sppidea.run

import com.intellij.execution.RunManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

// The build action is a separate action from the run config,
// so that it can be invoked from the Build tool window, and
// so that it can be disabled for test configurations, which
// "spp test" builds on its own.
class SppBuildAction : AnAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  // The action is only visible and enabled when a buildable
  // S++ run configuration is selected.
  override fun update(e: AnActionEvent) {
    val configuration = selectedSppConfiguration(e)
    e.presentation.isVisible = configuration != null
    e.presentation.isEnabled = configuration != null && configuration.kind.buildable
    e.presentation.icon = AllIcons.Actions.Compile
    e.presentation.text = configuration?.let { "Build '${it.name}'" } ?: "Build"
  }

  // When the action is invoked, start a build for the selected
  // configuration.
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val configuration = selectedSppConfiguration(e) ?: return
    SppBuildLauncher.build(project, configuration)
  }

  // Return the selected S++ run configuration, or null if none
  // is selected.
  private fun selectedSppConfiguration(e: AnActionEvent): SppRunConfiguration? =
    e.project?.let { RunManager.getInstance(it).selectedConfiguration?.configuration as? SppRunConfiguration }
}
