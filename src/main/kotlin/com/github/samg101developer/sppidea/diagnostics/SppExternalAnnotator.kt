package com.github.samg101developer.sppidea.diagnostics

import com.github.samg101developer.sppidea.settings.resolveSppExecutable
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
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import java.awt.Font
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path
import kotlin.io.path.absolute

/** What one annotation pass needs to know to ask the compiler about a file. */
data class SppAnnotationRequest(
  val project: Project,
  val root: Path,
  val executable: String,
  val file: Path,
)

/**
 * Reports the compiler's own errors in the editor.
 *
 * The plugin's parser answers everything about syntax; nothing but the compiler knows types, what a name resolved to
 * or whether a value was still there to use, so those errors come from running it. Each error carries the place it is
 * reported and the places that explain it, and both are shown: the first as the error itself, the rest as hints on
 * the code they point at.
 */
class SppExternalAnnotator : ExternalAnnotator<SppAnnotationRequest, List<SppDiagnostic>>() {

  override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): SppAnnotationRequest? {
    // A file the parser has already rejected is not worth a compile: the compiler will stop at the same place,
    // and the plugin has said so more precisely already.
    if (hasErrors) return null

    val path = file.virtualFile?.takeIf { it.isInLocalFileSystem }?.toNioPathOrNullSafely() ?: return null
    val root = SppCompilerDiagnostics.projectRootFor(path) ?: return null
    val executable = resolveSppExecutable() ?: return null
    return SppAnnotationRequest(file.project, root, executable, path)
  }

  override fun doAnnotate(collectedInfo: SppAnnotationRequest?): List<SppDiagnostic>? {
    val request = collectedInfo ?: return null

    // The compiler reads the files on disk, so what is being looked at has to be on disk before it runs.
    ApplicationManager.getApplication().invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // The whole project is indexed, not just the file being annotated: it is the same compile either way, and it
    // means moving to another file needs no further one.
    val analysis = SppCompilerDiagnostics.getInstance(request.project)
      .analyse(request.root, request.executable)
    return analysis.diagnostics[request.file.toString()].orEmpty()
  }

  override fun apply(file: PsiFile, annotationResult: List<SppDiagnostic>?, holder: AnnotationHolder) {
    val diagnostics = annotationResult?.takeIf { it.isNotEmpty() } ?: return
    val document = file.viewProvider.document ?: return
    val path = file.virtualFile?.toNioPathOrNullSafely()?.toString() ?: return

    for (diagnostic in diagnostics) {
      for (label in diagnostic.labels) {
        if (label.generated || label.file != path) continue
        val range = label.rangeIn(document) ?: continue

        if (label.primary) {
          holder.newAnnotation(HighlightSeverity.ERROR, "${diagnostic.code}: ${label.message}")
            .range(range)
            .tooltip(diagnostic.tooltip(label))
            .create()
          dimWhatWasNeverReached(file, holder, range.endOffset)
        } else {
          // The places that explain the error, on the code they point at. They are not themselves errors,
          // so they are reported at a severity that adds a tooltip and no colour of its own.
          holder.newAnnotation(HighlightSeverity.INFORMATION, label.message)
            .range(range)
            .tooltip(diagnostic.tooltip(label))
            .create()
        }
      }
    }
  }
}

/**
 * Grey out the rest of the function an error stopped in.
 *
 * Analysis recovers one member at a time: when a function fails, what follows the mistake inside it is never looked
 * at, so none of it has been checked and none of its names resolved. Showing it as ordinary code says otherwise, and
 * the first question anyone asks of the editor there - what is this, where does it come from - has no answer until
 * the error above is fixed.
 */
private fun dimWhatWasNeverReached(file: PsiFile, holder: AnnotationHolder, from: Int) {
  val enclosing = functionAround(file.findElementAt(from)) ?: return
  val to = enclosing.textRange.endOffset
  if (to <= from) return

  holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
    .range(TextRange(from, to))
    .enforcedTextAttributes(UNREACHED)
    .create()
}

/** The function a position sits in, which is as far as the recovery threw away. */
private fun functionAround(element: PsiElement?): PsiElement? = PsiTreeUtil.findFirstParent(element) {
  it is SppSubroutinePrototype || it is SppCoroutinePrototype
}

/** Dimmed, and italic like the explanations: it is not code the compiler has anything to say about. */
private val UNREACHED = TextAttributes().apply {
  foregroundColor = JBColor(0x9B9B9B, 0x6B6B6B)
  fontType = Font.ITALIC
}

/** The label's place in [document], or null when it falls outside what the editor currently holds. */
private fun SppLabel.rangeIn(document: Document): TextRange? {
  if (startLine < 0 || startLine >= document.lineCount) return null
  val lineStart = document.getLineStartOffset(startLine)
  val lineEnd = document.getLineEndOffset(startLine)
  val start = (lineStart + startCharacter).coerceIn(lineStart, lineEnd)

  val end = when {
    endLine != startLine || endCharacter <= startCharacter -> start + 1
    else -> lineStart + endCharacter
  }.coerceIn(start, lineEnd)

  // An empty range highlights nothing, so a span the editor has since shortened is widened back to one character.
  return if (end > start) TextRange(start, end) else TextRange(start, (start + 1).coerceAtMost(document.textLength))
}

/** The error as html: what it says here, then what it says everywhere else, then the note and the help. */
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

private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)

/**
 * The file's path, or null when it has none the compiler could read - a scratch or in-memory file, which
 * [com.intellij.openapi.vfs.VirtualFile.toNioPath] throws over rather than answering.
 */
private fun com.intellij.openapi.vfs.VirtualFile.toNioPathOrNullSafely(): Path? =
  runCatching { toNioPath().absolute() }.getOrNull()
