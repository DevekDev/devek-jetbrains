package com.devek.dev

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.OnClose
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.glassfish.tyrus.client.ClientManager
import java.awt.BorderLayout
import java.net.InetAddress
import java.net.URI
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.icons.AllIcons
import com.intellij.ui.dsl.builder.*
import javax.swing.JLabel
import com.intellij.openapi.wm.ToolWindowManager

@Service
class DevekPluginService(private val project: Project) {
    private var session: Session? = null
    private val environment: String = determineEnvironment()
    private val computerName: String = determineComputerName()
    private var authToken: String? = null
    private var webviewDialog: WebviewDialog? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectInterval = 5000L
    private val settings = project.service<DevekSettings>()

    init {
        if (loadSavedToken()) {
            connectToWebSocket()
        } else {
            showLoginPrompt()
        }
    }

    fun showMenu() {
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
            val changeData = Json.encodeToString(changeRequest)
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
            webviewDialog = WebviewDialog(project)
            webviewDialog!!.show()
        }
    }

    private fun showLoginPrompt() {
        ToolWindowManager.getInstance(project).getToolWindow("Devek.dev")?.show()
    }

    fun handleLoginAttempt(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return

        updateStatus("connecting")
        val loginRequest = LoginRequest(
            data = LoginData(email = email, password = password)
        )
        val loginData = Json.encodeToString(loginRequest)
        connectToWebSocket(loginData)
    }

    private fun handleLogout() {
        authToken = null
        saveToken(null)
        session?.close()
        updateStatus("disconnected")
        showLoginPrompt()
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
        DevekService.getInstance(project).updateStatus(status)
    }

    private fun connectToWebSocket(initialMessage: String? = null) {
        try {
            val client = ClientManager.createClient()
            val uri = URI("wss://ws.devek.dev")
            session = client.connectToServer(WebSocketHandler(this), uri)
            println("Connected to WebSocket server.")

            if (authToken != null) {
                sendAuthToken()
            } else if (initialMessage != null) {
                session?.basicRemote?.sendText(initialMessage)
            }
        } catch (e: Exception) {
            println("Failed to connect to WebSocket server.")
            e.printStackTrace()
            handleReconnection()
        }
    }

    private fun sendAuthToken() {
        val authRequest = AuthRequest(token = authToken ?: return)
        val authMessage = Json.encodeToString(authRequest)
        session?.basicRemote?.sendText(authMessage)
    }

    private fun handleReconnection() {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            updateStatus("connecting")
            Thread.sleep(reconnectInterval)
            connectToWebSocket()
        } else {
            updateStatus("error")
            Messages.showErrorDialog(
                project,
                "Failed to connect to Devek.dev server.",
                "Connection Error"
            )
        }
    }

    @ClientEndpoint
    private inner class WebSocketHandler(private val service: DevekPluginService) {
        @OnMessage
        fun onMessage(message: String) {
            try {
                val response = Json.decodeFromString<WebSocketResponse>(message)
                when (response.type) {
                    "init" -> {
                        updateStatus("connecting")
                        reconnectAttempts = 0
                    }
                    "auth" -> {
                        if (response.status == "success") {
                            val token = response.token
                            if (token != null) {
                                saveToken(token)
                                updateStatus("connected")
                            }
                        } else {
                            handleLogout()
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error processing message: $message")
                e.printStackTrace()
            }
        }

        @OnClose
        fun onClose(session: Session, reason: jakarta.websocket.CloseReason) {
            println("WebSocket closed: ${reason.reasonPhrase}")
            updateStatus("disconnected")
            handleReconnection()
        }
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
            browser.dispose()
            super.dispose()
        }
    }

    companion object {
        fun getInstance(project: Project): DevekPluginService = project.service()
    }
}