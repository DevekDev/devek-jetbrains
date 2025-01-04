package com.devek.dev

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import jakarta.websocket.Session
import kotlinx.serialization.encodeToString
import java.awt.BorderLayout
import java.net.InetAddress
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.application.ApplicationManager
import com.devek.dev.json


@Service
class DevekPluginService(private val project: Project) {
    private var session: Session? = null
    private var webSocketHandler: WebSocketHandler? = null
    private val environment: String = determineEnvironment()
    private val computerName: String = determineComputerName()
    private var authToken: String? = null
    private val settings = project.service<DevekSettings>()
    private var webviewDialog: WebviewDialog? = null
    private val statusListeners = mutableListOf<(String) -> Unit>()

    init {
        initializeWebSocket()
        if (loadSavedToken()) {
            webSocketHandler?.connect()
        } else {
            showLoginPrompt()
        }
    }

    private fun initializeWebSocket() {
        webSocketHandler = WebSocketHandler(
            project = project,
            json = json,
            onToken = { token -> saveToken(token) },
            onConnectionStatus = { status -> updateStatus(status) },
            onShowWebview = { showWebview() },
            getAuthToken = { authToken },
            onSessionUpdated = { newSession -> session = newSession }
        )
    }

    fun addStatusListener(listener: (String) -> Unit) {
        statusListeners.add(listener)
    }

    fun showMenu() {
        if (!isConnected()) {
            showLoginPrompt()
            return
        }

        val options = arrayOf(
            "View App",
            "View Status",
            "Logout",
            "Reconnect",
            "Learn More"
        )
        val choice = Messages.showDialog(
            project,
            "Devek.dev Options",
            "Devek.dev",
            options,
            0,
            Messages.getQuestionIcon()
        )

        when (choice) {
            0 -> showWebview()
            1 -> showConnectionStatus()
            2 -> handleLogout()
            3 -> connectToWebSocket()
            4 -> BrowserUtil.browse("https://devek.dev")
        }
    }

    fun isConnected(): Boolean = session?.isOpen == true && authToken != null

    fun sendCodeChange(event: DocumentEvent, document: Document) {
        val timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
        val startLine = document.getLineNumber(event.offset)
        val startChar = event.offset - document.getLineStartOffset(startLine)
        val endLine = document.getLineNumber(event.offset + event.newLength)
        val endChar = (event.offset + event.newLength) - document.getLineStartOffset(endLine)

        val changeRequest = ChangeRequest(
            data = ChangeData(
                document_uri = event.document.toString(),
                timestamp = timestamp,
                start_line = startLine,
                start_character = startChar,
                end_line = endLine,
                end_character = endChar,
                text = event.newFragment.toString(),
                environment = environment,
                computer_name = computerName
            )
        )

        try {
            val changeData = json.encodeToString(changeRequest)
            session?.basicRemote?.sendText(changeData)
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to send change data")
        }
    }

    private fun showConnectionStatus() {
        val status = if (session?.isOpen == true) "Connected" else "Disconnected"
        val message = """
            Status: $status
            Device: $computerName
            Environment: $environment
        """.trimIndent()

        Messages.showMessageDialog(
            project,
            message,
            "Connection Status",
            Messages.getInformationIcon()
        )
    }

    private fun showWebview() {
        if (webviewDialog == null || !webviewDialog!!.isVisible) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Devek.dev")
            if (toolWindow != null) {
                val browser = JBCefBrowser("https://app.devek.dev")
                val content = toolWindow.contentManager.factory.createContent(
                    browser.component,
                    "",
                    false
                )
                toolWindow.contentManager.removeAllContents(true)
                toolWindow.contentManager.addContent(content)
                toolWindow.show()
            }
        }
    }

    private fun showLoginPrompt() {
        ToolWindowManager.getInstance(project).getToolWindow("Devek.dev")?.show()
    }

    fun handleLoginAttempt(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return

        updateStatus("connecting")
        val loginRequest = LoginRequest(
            type = "login",
            data = LoginData(email = email, password = password)
        )
        val loginData = json.encodeToString(loginRequest)
        webSocketHandler?.connect(loginData)
    }

    private fun handleLogout() {
        authToken = null
        saveToken(null)

        webSocketHandler?.dispose()
        webSocketHandler = null
        session = null

        updateStatus("disconnected")

        ApplicationManager.getApplication().invokeLater {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Devek.dev")
            toolWindow?.let {
                it.contentManager.removeAllContents(true)
                showLoginPrompt()
            }

            // Reinitialize websocket handler after logout
            initializeWebSocket()
        }
    }

    private fun determineEnvironment(): String {
        return System.getProperty("idea.platform.prefix") ?: "Unknown"
    }

    private fun determineComputerName(): String {
        return try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "Unknown-Computer"
        }
    }

    private fun loadSavedToken(): Boolean {
        authToken = settings.state.authToken
        return authToken != null
    }

    private fun saveToken(token: String?) {
        settings.state.authToken = token
        authToken = token
    }

    private fun updateStatus(status: String) {
        statusListeners.forEach { it(status) }
    }
    private fun connectToWebSocket() {
        webSocketHandler?.connect()
    }

    private inner class WebviewDialog(project: Project) : DialogWrapper(project) {
        private val browser = JBCefBrowser("https://app.devek.dev")

        init {
            title = "Devek.dev"
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(BorderLayout()).apply {
                preferredSize = JBUI.size(800, 600)
                add(browser.component, BorderLayout.CENTER)
            }
        }

        override fun dispose() {
            webSocketHandler?.dispose()
        }
    }

    companion object {
        fun getInstance(project: Project): DevekPluginService = project.service()
    }
}