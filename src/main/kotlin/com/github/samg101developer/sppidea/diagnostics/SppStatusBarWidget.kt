package com.github.samg101developer.sppidea.diagnostics

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

/**
 * Says in the status bar when the compiler is working.
 *
 * The gutter says whether the file being looked at is known; this says whether anything is happening at all, which is
 * the other half of the question when an answer is missing: waiting, or nothing coming.
 */
class SppStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ID

    override fun getDisplayName(): String = "S++ Analysis"

    override fun createWidget(project: Project): StatusBarWidget = SppStatusBarWidget(project)

    companion object {
        const val ID = "SppAnalysisStatus"
    }
}

private class SppStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var statusBar: StatusBar? = null

    override fun ID(): String = SppStatusBarWidgetFactory.ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        poll()
    }

    override fun dispose() {
        Disposer.dispose(alarm)
        statusBar = null
    }

    // A compile is started and finished by a background thread with nothing to notify from, so the widget asks. It is
    // one boolean read a second, and it stops as soon as the widget goes away.
    private fun poll() {
        if (alarm.isDisposed) return
        statusBar?.updateWidget(SppStatusBarWidgetFactory.ID)
        alarm.addRequest(::poll, POLL_MS)
    }

    override fun getText(): String = when {
        SppCompilerDiagnostics.getInstance(project).isAnalysing() -> "S++: analysing…"
        else -> ""
    }

    override fun getTooltipText(): String = "The S++ compiler is working out what the names in this project mean"

    override fun getAlignment(): Float = java.awt.Component.LEFT_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    private companion object {
        const val POLL_MS = 1000
    }
}
