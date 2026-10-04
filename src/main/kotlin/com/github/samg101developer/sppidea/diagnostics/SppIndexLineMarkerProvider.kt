package com.github.samg101developer.sppidea.diagnostics

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.lang.psi.SppCoroutinePrototype
import com.intellij.lang.psi.SppSubroutinePrototype
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.lang.SppSyntaxHighlighter
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.absolute

// A link marker provider that indicates the status of
// analysis for a particular function. It can be non-analysed,
// in-analysis, analysed, or failed analysis. Currently,
// there is no incremental compilation so all functions
// will be the same state, but with incremental compilation,
// the state of each function will be independent.
class SppIndexLineMarkerProvider : LineMarkerProvider {

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    // Leaves only: the platform asks for every element, and
    // a marker on a parent as well as its child paints the
    // same line twice.
    if (element.firstChild != null) return null
    val prototype = element.parent?.parent ?: return null
    if (prototype !is SppSubroutinePrototype && prototype !is SppCoroutinePrototype) return null
    if (prototypeName(prototype) !== element.parent) return null

    val file = element.containingFile?.virtualFile
      ?.let { runCatching { it.toNioPath().absolute() }.getOrNull() } ?: return null
    val state = stateOf(element, file, prototype)

    return LineMarkerInfo(
      element,
      element.textRange,
      state.icon,
      { state.tooltip },
      null,
      GutterIconRenderer.Alignment.LEFT,
      { state.tooltip },
    )
  }

  private fun prototypeName(prototype: PsiElement): PsiElement? = when (prototype) {
    is SppSubroutinePrototype -> prototype.identifier
    is SppCoroutinePrototype -> prototype.identifier
    else -> null
  }

  private fun stateOf(element: PsiElement, file: Path, prototype: PsiElement): IndexState {
    val service = SppCompilerDiagnostics.getInstance(element.project)

    // A function whose analysis stopped part way holds names
    // the compiler never reached, so its gutter says so rather
    // than claiming the whole of it is known.
    val document = element.containingFile?.viewProvider?.document
    val failed = document != null && service.cachedDiagnosticsFor(file).any { diagnostic ->
      diagnostic.labels.any { label ->
        label.primary && SppSymbolLookup.rangeOf(label.asSpan(), document)
          ?.let { prototype.textRange.contains(it.startOffset) } == true
      }
    }

    return IndexState.of(
      analysing = service.isAnalysing(),
      analysed = service.isAnalysed(file),
      failed = failed
    )
  }

  // The state of a function's analysis, and what the gutter
  // shows for it.
  internal enum class IndexState(val icon: Icon, val tooltip: String) {
    PENDING(
      CircleIcon(SppSyntaxHighlighter.INDEX_PENDING),
      "Not analysed yet - navigation and hover answer once the compiler has run over this file",
    ),
    ANALYSING(
      CircleIcon(SppSyntaxHighlighter.INDEX_ANALYSING),
      "Analysing - the compiler is working out what the names in this project mean",
    ),
    INDEXED(
      CircleIcon(SppSyntaxHighlighter.INDEX_INDEXED),
      "Analysed - the names in this function can be navigated and hovered",
    ),
    INCOMPLETE(
      CircleIcon(SppSyntaxHighlighter.INDEX_INCOMPLETE),
      "Analysis stopped at an error in this function, so the names after it were never worked out",
    ),
    ;

    internal companion object {
      // Based on the flags of the compiler service, return the
      // state of the function's analysis.
      fun of(analysing: Boolean, analysed: Boolean, failed: Boolean): IndexState = when {
        analysing -> ANALYSING
        !analysed -> PENDING
        failed -> INCOMPLETE
        else -> INDEXED
      }
    }
  }
}

// A filled dot, drawn rather than shipped: it is one shape in
// several colours, read from the scheme as it is drawn.
private class CircleIcon(private val colour: ColorKey, private val size: Int = 8) : Icon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.color = EditorColorsManager.getInstance().globalScheme.getColor(colour) ?: colour.defaultColor
      g2.fillOval(x, y, size, size)
    } finally {
      g2.dispose()
    }
  }

  override fun getIconWidth(): Int = size

  override fun getIconHeight(): Int = size
}
