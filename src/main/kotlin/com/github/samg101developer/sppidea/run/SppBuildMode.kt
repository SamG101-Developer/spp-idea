package com.github.samg101developer.sppidea.run

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.Icon

// The modes "spp" builds in, as given to its "--mode" option.
enum class SppBuildMode(val cliArg: String) {
  DEV("dev"),
  REL("rel"),
}

// A build mode as an execution target: the platform shows the
// targets of the selected run configuration next to it in the
// toolbar ("app | dev"), as CLion does with its CMake profiles,
// and remembers the one chosen for the project. So the mode is
// picked once, and every S++ configuration builds and runs in it.
class SppModeTarget private constructor(val mode: SppBuildMode) : ExecutionTarget() {
  override fun getId(): String = "spp.mode.${mode.cliArg}"

  override fun getDisplayName(): String = mode.cliArg

  override fun getIcon(): Icon = AllIcons.General.Settings

  override fun canRun(configuration: RunConfiguration): Boolean = configuration is SppRunProfile

  companion object {
    val ALL: List<SppModeTarget> = SppBuildMode.entries.map(::SppModeTarget)

    fun of(mode: SppBuildMode): SppModeTarget = ALL.first { it.mode == mode }

    // The mode a configuration runs in: the target it was started
    // on, else the project's selected one, else the kind's default.
    fun modeFor(profile: SppRunProfile, target: ExecutionTarget?): SppBuildMode =
      (target as? SppModeTarget)?.mode
        ?: (ExecutionTargetManager.getActiveTarget(profile.project) as? SppModeTarget)?.mode
        ?: profile.kind.defaultMode
  }
}

// Offers the build modes as the targets of every S++ run
// configuration.
class SppModeTargetProvider : ExecutionTargetProvider() {
  override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> =
    if (configuration is SppRunProfile) SppModeTarget.ALL else emptyList()
}
