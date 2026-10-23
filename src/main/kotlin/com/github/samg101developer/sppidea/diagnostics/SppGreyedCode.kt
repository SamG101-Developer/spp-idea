package com.github.samg101developer.sppidea.diagnostics

import com.intellij.lang.psi.SppCoroutinePrototype
import com.intellij.lang.psi.SppSubroutinePrototype
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

// What a file has greyed out: the code an error stopped the
// analysis of, and the code "!cfg" left out of the build.
data class SppGreyed(
  val unreached: List<TextRange>,
  val inactive: List<TextRange>,
) {
  val all: List<TextRange> get() = unreached + inactive
}

// The greyed code in a file, worked out once per edit or run and
// shared: the external annotator draws it, and the annotators add
// no colour inside it, as colour on the same highlighter layer
// can be drawn over the grey.
object SppGreyedCode {

  // What [file] has greyed out, cached until it is edited or a
  // run reports on it again.
  fun of(file: PsiFile): SppGreyed = CachedValuesManager.getCachedValue(file) {
    val service = SppCompilerDiagnostics.getInstance(file.project)
    CachedValueProvider.Result.create(compute(file), PsiModificationTracker.getInstance(file.project), service)
  }

  // Whether [range] in [file] is greyed out.
  fun covers(file: PsiFile, range: TextRange): Boolean = of(file).all.any { it.contains(range) }

  private fun compute(file: PsiFile): SppGreyed {
    val document = file.viewProvider.document ?: return SppGreyed(emptyList(), emptyList())
    val path = file.virtualFile?.toNioPathOrNullSafely() ?: return SppGreyed(emptyList(), emptyList())
    val service = SppCompilerDiagnostics.getInstance(file.project)
    val inactive = service.cachedInactiveFor(path)
      .mapNotNull { SppSymbolLookup.regionOf(it.where, document) }
      .filter { (start, end) -> end > start }
      .map { (start, end) -> TextRange(start, end) }
    return SppGreyed(unreached(file, document, service.cachedDiagnosticsFor(path), path.toString()), inactive)
  }

  // The bodies of the functions an error stopped in, but for the
  // lines an error or its explanations point at, which stay
  // readable.
  private fun unreached(file: PsiFile, document: Document, diagnostics: List<SppDiagnostic>, path: String)
    : List<TextRange> {
    val labels = diagnostics.flatMap { it.labels }.filter { !it.generated && it.file == path }
    val labelled = labels.flatMap { it.startLine..maxOf(it.startLine, it.endLine) }.toSet()
    return labels.asSequence()
      .filter { it.primary }
      .mapNotNull { SppSymbolLookup.offsetOf(it.asSpan(), document) }
      .mapNotNull { functionAround(file.findElementAt(it)) }
      .distinct()
      .flatMap { unreachedIn(it, document, labelled) }
      .toList()
  }

  // The runs of unlabelled lines in [function]'s body, its braces
  // left out.
  private fun unreachedIn(function: PsiElement, document: Document, labelled: Set<Int>): List<TextRange> {
    val body = when (function) {
      is SppSubroutinePrototype -> function.functionImplementation
      is SppCoroutinePrototype -> function.functionImplementation
      else -> null
    } ?: return emptyList()
    val firstLine = document.getLineNumber(body.textRange.startOffset) + 1
    val lastLine = document.getLineNumber(body.textRange.endOffset) - 1
    if (firstLine > lastLine) return emptyList()

    val ranges = mutableListOf<TextRange>()
    var runStart = -1
    for (line in firstLine..lastLine + 1) {
      val dim = line <= lastLine && line !in labelled
      if (dim && runStart < 0) runStart = line
      if (!dim && runStart >= 0) {
        val start = document.getLineStartOffset(runStart)
        val end = document.getLineEndOffset(line - 1)
        if (end > start) ranges += TextRange(start, end)
        runStart = -1
      }
    }
    return ranges
  }

  // The function a position sits in, which is as far as the
  // recovery threw away.
  private fun functionAround(element: PsiElement?): PsiElement? = PsiTreeUtil.findFirstParent(element) {
    it is SppSubroutinePrototype || it is SppCoroutinePrototype
  }
}
