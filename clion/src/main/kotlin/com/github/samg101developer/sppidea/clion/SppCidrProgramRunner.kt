package com.github.samg101developer.sppidea.clion

import com.github.samg101developer.sppidea.run.SppRunProfile
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.executeState

// Runs S++ configurations in CLion. Being C++ run configurations
// as far as CLion is concerned, they would otherwise be picked
// up by CLion's own runners (run, coverage, Valgrind, profiler),
// which expect CLion's command line state and fail on ours. This
// runner is registered first, so it claims them all: "Run" runs
// "spp" as usual, and the others report that they do not apply.
// Debug is left alone: the configuration suppresses it, so no
// runner offers it.
class SppCidrProgramRunner : ProgramRunner<RunnerSettings> {
  override fun getRunnerId(): String = "SppCidrProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean =
    profile is SppRunProfile && executorId != DefaultDebugExecutor.EXECUTOR_ID

  override fun execute(environment: ExecutionEnvironment) {
    if (environment.executor.id != DefaultRunExecutor.EXECUTOR_ID) {
      throw ExecutionException("S++ configurations can only be run, not with '${environment.executor.actionName}'.")
    }
    ExecutionManager.getInstance(environment.project).startRunProfile(environment) { state ->
      executeState(state, environment, this)
    }
  }
}
