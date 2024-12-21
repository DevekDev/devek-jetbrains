package com.devek.dev

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.StatusBarWidgetFactory

@Service
class DevekService(private val project: Project) {
    private var statusWidget: DevekStatusBarWidgetFactory.DevekStatusBarWidget? = null
    protected var codeChangeListener: CodeChangeListener? = null

    fun registerWidget(widget: DevekStatusBarWidgetFactory.DevekStatusBarWidget) {
        statusWidget = widget
    }

    fun registerCodeChangeListener(listener: CodeChangeListener) {
        codeChangeListener = listener
    }

    fun updateStatus(status: String) {
        statusWidget?.updateStatus(status)
    }

    fun handleWidgetClick() {
        codeChangeListener?.showMenu()
    }

    companion object {
        fun getInstance(project: Project): DevekService = project.service()
    }
}

class DevekStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "DevekStatusWidget"
    override fun getDisplayName(): String = "Devek.dev"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = DevekStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    class DevekStatusBarWidget(private val project: Project) : StatusBarWidget {
        private var currentStatus = "disconnected"
        private var myStatusBar: StatusBar? = null

        fun updateStatus(status: String) {
            currentStatus = status
            myStatusBar?.updateWidget(ID())
        }

        override fun ID(): String = "DevekStatusWidget"

        override fun install(statusBar: StatusBar) {
            myStatusBar = statusBar
        }

        override fun dispose() {
            myStatusBar = null
        }

        override fun getPresentation(): StatusBarWidget.TextPresentation = object : StatusBarWidget.TextPresentation {
            override fun getText(): String {
                return when (currentStatus) {
                    "connected" -> "✓ Devek.dev"
                    "connecting" -> "⟳ Devek.dev"
                    "error" -> "✕ Devek.dev"
                    else -> "⚪ Devek.dev"
                }
            }

            override fun getTooltipText(): String {
                return when (currentStatus) {
                    "connected" -> "Connected to Devek.dev - Click to view options"
                    "connecting" -> "Connecting to Devek.dev..."
                    "error" -> "Connection error - Click to retry"
                    else -> "Click to login to Devek.dev"
                }
            }

            override fun getClickConsumer(): Consumer<MouseEvent>? =
                Consumer {
                    DevekService.getInstance(project).handleWidgetClick()
                }

            override fun getAlignment(): Float = 0f
        }
    }
}