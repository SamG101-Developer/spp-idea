package com.github.samg101developer.sppidea.run

import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

// The platform keeps one active execution target per project.
// Selecting an S++ configuration makes it a build mode, so on
// going back to e.g. a CMake configuration in CLion, the
// profile picked there (say "Release") is no longer a target of
// it, and the platform falls back to the first one ("Debug");
// likewise "rel" is lost going the other way. So the last
// target of each kind is remembered, and put back whenever the
// selection moves from one kind of configuration to the other.
@Service(Service.Level.PROJECT)
class SppTargetMemory {
  internal var lastActive: ExecutionTarget? = null
  internal var lastSppTargetId: String? = null
  internal var lastOtherTargetId: String? = null
}

class SppTargetMemoryListener(private val project: Project) : ExecutionTargetListener {
  override fun activeTargetChanged(newTarget: ExecutionTarget) {
    // The default target is what a configuration without targets
    // (or no configuration) has; it says nothing about either kind.
    if (newTarget == DefaultExecutionTarget.INSTANCE) return

    val memory = project.service<SppTargetMemory>()
    val isSpp = newTarget is SppModeTarget
    val previous = memory.lastActive
    memory.lastActive = newTarget

    // The kind changed, so the platform chose this target as a
    // fallback; put back the one last used for this kind, if the
    // selected configuration offers it. Its change comes back
    // through here, as the same kind, and is remembered then.
    if (previous != null && (previous is SppModeTarget) != isSpp) {
      val wanted = if (isSpp) memory.lastSppTargetId else memory.lastOtherTargetId
      val configuration = RunManager.getInstance(project).selectedConfiguration?.configuration
      if (wanted != null && wanted != newTarget.id && configuration != null) {
        val manager = ExecutionTargetManager.getInstance(project)
        val target = manager.getTargetsFor(configuration).firstOrNull { it.id == wanted }
        if (target != null) {
          ApplicationManager.getApplication().invokeLater({
            if (manager.activeTarget == newTarget) manager.activeTarget = target
          }, project.disposed)
          return
        }
      }
    }

    if (isSpp) memory.lastSppTargetId = newTarget.id else memory.lastOtherTargetId = newTarget.id
  }
}
