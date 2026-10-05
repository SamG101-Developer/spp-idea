package com.github.samg101developer.sppidea.diagnostics

import com.intellij.lang.SppFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import java.nio.file.Path

// The project tree underlines a file holding an error, and
// every folder above it, with a red squiggle, but only for
// the file types a filter accepts.
class SppProblemFileHighlightFilter : Condition<VirtualFile> {
  override fun value(file: VirtualFile): Boolean = file.fileType == SppFileType.INSTANCE
}

// Tells the project tree which files hold an error, from
// the compiler's information rather than the editor's
// highlighting.
class SppProblemFiles(private val project: Project) {

  // The files this marked last time, so a file whose error
  // has gone can be unmarked.
  private var marked: Set<VirtualFile> = emptySet()

  // Mark every file that contains an error, by its primary
  // label (where the error line is), and unmark every file
  // that no longer holds one. Each run compiles the whole
  // project, so its answer is the full set.
  fun update(diagnostics: Map<String, List<SppDiagnostic>>) {
    val paths = diagnostics.values.asSequence()
      .flatten()
      .mapNotNull { it.primaryLabel?.file }
      .toSet()

    // The problem solver refuses to be told on the UI thread,
    // and a run is always on a background one, so this only
    // moves off the UI thread, should a caller ever be on it.
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) app.executeOnPooledThread { apply(paths) } else apply(paths)
  }

  // One run's answer at a time, so two overlapping runs cannot
  // interleave their marking and unmarking.
  @Synchronized
  private fun apply(paths: Set<String>) {
    if (project.isDisposed) return
    val fs = LocalFileSystem.getInstance()
    val now = paths.mapNotNull { runCatching { fs.findFileByNioFile(Path.of(it)) }.getOrNull() }.toSet()
    val wolf = WolfTheProblemSolver.getInstance(project)
    (marked - now).forEach { wolf.clearProblemsFromExternalSource(it, this) }
    (now - marked).forEach { wolf.reportProblemsFromExternalSource(it, this) }
    marked = now
  }
}
