package com.github.samg101developer.sppidea.diagnostics

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.lang.psi.SppCoroutinePrototype
import com.intellij.lang.psi.SppSubroutinePrototype
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.absolute

/**
 * Says, in the gutter, whether a function's names have been worked out yet.
 *
 * Navigation and hover can only answer from what the last analysis found, and an analysis is a compile of the project
 * that happens shortly after a file is opened or edited. Without a mark there is no way to tell "this name has no
 * declaration" from "nothing has been worked out for this file yet", which are very different answers.
 */
class SppIndexLineMarkerProvider : LineMarkerProvider {

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    // Leaves only: the platform asks for every element, and a marker on a parent as well as its child paints the
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

    // A function whose analysis stopped part way holds names the compiler never reached, so its gutter says so
    // rather than claiming the whole of it is known.
    val document = element.containingFile?.viewProvider?.document
    val failed = document != null && service.cachedDiagnosticsFor(file).any { diagnostic ->
      diagnostic.labels.any { label ->
        label.primary && SppSymbolLookup.rangeOf(label.asSpan(), document)
          ?.let { prototype.textRange.contains(it.startOffset) } == true
      }
    }

    return IndexState.of(analysing = service.isAnalysing(), analysed = service.isAnalysed(file), failed = failed)
  }

  internal enum class IndexState(val icon: Icon, val tooltip: String) {
    PENDING(
      CircleIcon(JBColor.GRAY),
      "Not analysed yet - navigation and hover answer once the compiler has run over this file",
    ),
    ANALYSING(
      CircleIcon(JBColor(0xE8C200, 0xD9B400)),
      "Analysing - the compiler is working out what the names in this project mean",
    ),
    INDEXED(
      CircleIcon(JBColor(0x59A869, 0x499C54)),
      "Analysed - the names in this function can be navigated and hovered",
    ),
    INCOMPLETE(
      CircleIcon(JBColor(0xDB5860, 0xC75450)),
      "Analysis stopped at an error in this function, so the names after it were never worked out",
    ),
    ;

    internal companion object {
      /**
       * What the gutter shows, given what is known and what is happening.
       *
       * A run under way is said first, whether or not there is an older answer: it is the difference between an
       * answer that is missing and one that is on its way, which is the whole point of the mark. A function that
       * stopped at an error is next, because "analysed" would overstate what is known about it.
       */
      fun of(analysing: Boolean, analysed: Boolean, failed: Boolean): IndexState = when {
        analysing -> ANALYSING
        !analysed -> PENDING
        failed -> INCOMPLETE
        else -> INDEXED
      }
    }
  }
}

/** A filled dot, drawn rather than shipped: it is one shape in three colours. */
private class CircleIcon(private val colour: JBColor, private val size: Int = 8) : Icon {

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.color = colour
      g2.fillOval(x, y, size, size)
    } finally {
      g2.dispose()
    }
  }

  override fun getIconWidth(): Int = size

  override fun getIconHeight(): Int = size
}
