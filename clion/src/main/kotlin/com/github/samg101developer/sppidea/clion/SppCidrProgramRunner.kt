package com.github.samg101developer.sppidea.clion

import com.github.samg101developer.sppidea.run.SppRunProfile
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise

// Runs S++ configurations in CLion. Being C++ run configurations
// as far as CLion is concerned, they would otherwise be picked
// up by CLion's own runners (run, coverage, Valgrind, profiler),
// which expect CLion's command line state and fail on ours. This
// runner is registered first, so it claims them all: "Run" runs
// "spp" as usual, and the others report that they do not apply.
// Debug is left alone: the configuration suppresses it, so no
// runner offers it.
class SppCidrProgramRunner : AsyncProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "SppCidrProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean =
    profile is SppRunProfile && executorId != DefaultDebugExecutor.EXECUTOR_ID

  // Starting a process is not allowed on the UI thread (CLion
  // 2026.2 refuses outright), so "spp" is started on a pooled
  // thread, and only its console is shown back on the UI one.
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    if (environment.executor.id != DefaultRunExecutor.EXECUTOR_ID) {
      return rejectedPromise(ExecutionException("S++ configurations can only be run, not with '${environment.executor.actionName}'."))
    }

    val promise = AsyncPromise<RunContentDescriptor?>()
    val app = ApplicationManager.getApplication()
    app.executeOnPooledThread {
      try {
        val result = state.execute(environment.executor, this)
        app.invokeLater({ promise.setResult(result?.let { showRunContent(it, environment) }) }, ModalityState.any())
      } catch (e: ExecutionException) {
        promise.setError(e)
      }
    }
    return promise
  }
}
