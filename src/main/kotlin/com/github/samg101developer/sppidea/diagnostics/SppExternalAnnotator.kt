package com.github.samg101developer.sppidea.diagnostics

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
import com.intellij.lang.SppSyntaxHighlighter
import com.intellij.lang.psi.SppCoroutinePrototype
import com.intellij.lang.psi.SppSubroutinePrototype
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path
import kotlin.io.path.absolute

// What one annotation pass needs to know to ask the compiler
// about a file; the project, root path, s++ executable, and
// the file itself.
data class SppAnnotationRequest(
  val project: Project,
  val root: Path,
  val executable: String,
  val file: Path,
)

// The compiler's analysis of a file, which is a list of the
// errors it found in it, and the explanations of those errors
// that point at other files.
class SppExternalAnnotator : ExternalAnnotator<SppAnnotationRequest, List<SppDiagnostic>>() {

  // build the annotation request, by collecting the information
  // form the file and editor.
  override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): SppAnnotationRequest? {
    if (hasErrors) return null
    val path = file.virtualFile?.takeIf { it.isInLocalFileSystem }?.toNioPathOrNullSafely() ?: return null
    val root = SppCompilerDiagnostics.projectRootFor(path) ?: return null
    val executable = resolveSppExecutable() ?: return null
    return SppAnnotationRequest(file.project, root, executable, path)
  }

  // Invoke the main analysis of the compiler, which is a
  // compilation of the whole project, and return the errors it
  // found in this file.
  override fun doAnnotate(collectedInfo: SppAnnotationRequest?): List<SppDiagnostic>? {
    val request = collectedInfo ?: return null

    // The compiler reads the files on disk, so what is being
    // looked at has to be on disk before it runs; force a save
    // of all the documents.
    ApplicationManager.getApplication().invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // The whole project is indexed, not just the file being
    // annotated: it is the same compile either way, and it means
    // moving to another file needs no further one.
    val analysis = SppCompilerDiagnostics.getInstance(request.project)
      .analyse(request.root, request.executable)
    return analysis.diagnostics[request.file.toString()].orEmpty()
  }

  // The compiler's analysis of the file is now known, so add the
  // annotations to the editor. The errors are marked as errors,
  // and the explanations are marked as information, so they are
  // shown in the editor but do not colour the code they sit on.
  override fun apply(file: PsiFile, annotationResult: List<SppDiagnostic>?, holder: AnnotationHolder) {
    val diagnostics = annotationResult ?: return
    val document = file.viewProvider.document ?: return
    val path = file.virtualFile?.toNioPathOrNullSafely()?.toString() ?: return

    // Every line an error points at in this file, the explanations
    // included: those stay readable even when they sit in code the
    // error stopped analysis of.
    val labelled = diagnostics.asSequence()
      .flatMap { it.labels }
      .filter { !it.generated && it.file == path }
      .flatMap { it.startLine..maxOf(it.startLine, it.endLine) }
      .toSet()

    // The functions an error stopped in, each dimmed once however
    // many errors it holds.
    val failed = linkedSetOf<PsiElement>()

    for (diagnostic in diagnostics) {
      for (label in diagnostic.labels) {
        if (label.generated || label.file != path) continue
        val range = label.rangeIn(document) ?: continue

        // The primary label is for the error itself, which is
        // highlighted as an error and has a tooltip with all the
        // context.
        if (label.primary) {
          holder.newAnnotation(HighlightSeverity.ERROR, "${diagnostic.code}: ${label.message}")
            .range(range)
            .tooltip(diagnostic.tooltip(label))
            .create()
          functionAround(file.findElementAt(range.startOffset))?.let { failed += it }
        }
        // The places that explain the error, on the code they point
        // at. They are not themselves errors, so they are reported
        // at a severity that adds a tooltip and no colour of its own.
        else {
          holder.newAnnotation(HighlightSeverity.INFORMATION, label.message)
            .range(range)
            .tooltip(diagnostic.tooltip(label))
            .create()
        }
      }
    }

    // Dim all the lines that are not errors, if any errors are
    // present, and invoke the cmp highlighting, which is separate
    // from the error reporting.
    failed.forEach { dimWhatWasNeverReached(it, holder, document, labelled) }
    colourCmpUses(file, holder, document, failed)
  }

  // Add the "cmp" colouring to the uses of the constants and
  // generics the compiler resolved in this file, but not inside
  // a function an error stopped in, which is dimmed instead.
  private fun colourCmpUses(file: PsiFile, holder: AnnotationHolder, document: Document, failed: Set<PsiElement>) {
    val path = file.virtualFile?.toNioPathOrNullSafely() ?: return
    val dimmed = failed.map { it.textRange }
    SppCompilerDiagnostics.getInstance(file.project).cachedSymbolsFor(path)
      .filter { it.kind == "constant" || it.kind == "generic" }
      .mapNotNull { SppSymbolLookup.rangeOf(it.use, document) }
      .filter { range -> dimmed.none { it.contains(range) } }
      .distinct()
      .forEach { range ->
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
          .range(range)
          .textAttributes(SppSyntaxHighlighter.CMP_IDENTIFIER)
          .create()
      }
  }
}

// Grey out lines that are not labelled, as they are irrelevant
// to the error.
private fun dimWhatWasNeverReached(
  function: PsiElement, holder: AnnotationHolder, document: Document, labelled: Set<Int>,
) {
  // Extract the implementation of the function, for the first
  // and last lines to dim. If the function is not a subroutine
  // or coroutine, this is not a function so return.
  val body = when (function) {
    is SppSubroutinePrototype -> function.functionImplementation
    is SppCoroutinePrototype -> function.functionImplementation
    else -> null
  } ?: return
  val firstLine = document.getLineNumber(body.textRange.startOffset) + 1
  val lastLine = document.getLineNumber(body.textRange.endOffset) - 1
  if (firstLine > lastLine) return

  // Dim each line that is not labelled, and don't include the {}
  // tokens from the function's body.
  var runStart = -1
  for (line in firstLine..lastLine + 1) {
    val dim = line <= lastLine && line !in labelled
    if (dim && runStart < 0) runStart = line
    if (!dim && runStart >= 0) {
      val start = document.getLineStartOffset(runStart)
      val end = document.getLineEndOffset(line - 1)
      if (end > start) {
        // "TEXT_ATTRIBUTES" is drawn on a layer above the colouring
        // the other annotators add at "INFORMATION", which would
        // otherwise win wherever both apply, and below the errors.
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
          .range(TextRange(start, end))
          .textAttributes(SppSyntaxHighlighter.UNREACHED_CODE)
          .create()
      }
      runStart = -1
    }
  }
}

// The function a position sits in, which is as far as the
// recovery threw away.
private fun functionAround(element: PsiElement?): PsiElement? = PsiTreeUtil.findFirstParent(element) {
  it is SppSubroutinePrototype || it is SppCoroutinePrototype
}

// The label's place in [document], or null when it falls
// outside what the editor currently holds. */
private fun SppLabel.rangeIn(document: Document): TextRange? {
  if (startLine < 0 || startLine >= document.lineCount) return null
  val lineStart = document.getLineStartOffset(startLine)
  val lineEnd = document.getLineEndOffset(startLine)
  val start = (lineStart + startCharacter).coerceIn(lineStart, lineEnd)

  val end = when {
    endLine != startLine || endCharacter <= startCharacter -> start + 1
    else -> lineStart + endCharacter
  }.coerceIn(start, lineEnd)

  // An empty range highlights nothing, so a span the editor
  // has since shortened is widened back to one character.
  return if (end > start)
    TextRange(start, end) else
    TextRange(start, (start + 1).coerceAtMost(document.textLength))
}

// The error as HTML: what it says here, then what it says
// everywhere else, then the note and the help. This is for
// the tooltip.
private fun SppDiagnostic.tooltip(current: SppLabel): String {
  val parts = mutableListOf<String>()
  parts += "<b>${escape(code)}: ${escape(title)}</b>"
  parts += escape(current.message)

  labels.filter { it !== current && !it.generated }.forEach {
    val where = "${it.file.substringAfterLast('/')}:${it.startLine + 1}"
    parts += "<i>${escape(it.message)}</i> &mdash; $where"
  }

  if (note.isNotBlank()) parts += "<i>${escape(note)}</i>"
  if (help.isNotBlank()) parts += "<i>${escape(help)}</i>"
  return parts.joinToString("<br/>")
}

// Escape the text for HTML, so it is not interpreted as HTML.
private fun escape(text: String): String =
  StringUtil.escapeXmlEntities(text)

// The file's path, or null when it has none the compiler
// could read.
private fun com.intellij.openapi.vfs.VirtualFile.toNioPathOrNullSafely(): Path? =
  runCatching { toNioPath().absolute() }.getOrNull()
