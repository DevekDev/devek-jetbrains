package com.devek.dev

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.OnClose
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import org.glassfish.tyrus.client.ClientManager
import java.net.URI
import java.net.InetAddress
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.*
import java.awt.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.intellij.openapi.components.service
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

// Add the serializable data classes
@Serializable
data class LoginRequest(
    val type: String = "login",
    val data: LoginData
)

@Serializable
data class LoginData(
    val email: String,
    val password: String
)

@Serializable
data class AuthRequest(
    val type: String = "auth",
    val token: String
)

@Serializable
data class ChangeRequest(
    val type: String = "change",
    val data: ChangeData
)

@Serializable
data class ChangeData(
    val document_uri: String,
    val timestamp: String,
    val start_line: Int,
    val start_character: Int,
    val end_line: Int,
    val end_character: Int,
    val text: String,
    val environment: String,
    val computer_name: String
)

@Serializable
data class WebSocketResponse(
    val type: String,
    val status: String? = null,
    val token: String? = null,
    val message: String? = null
)

@State(
    name = "DevekSettings",
    storages = [Storage("devek.xml")]
)
@Service
class DevekSettings : PersistentStateComponent<DevekSettings.State> {
    data class State(var authToken: String? = null)
    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }
}

@ClientEndpoint
class CodeChangeListener(private val project: Project) : EditorFactoryListener {
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
        // Register with DevekService
        DevekService.getInstance(project).registerCodeChangeListener(this)
        if (loadSavedToken()) {
            connectToWebSocket()
        } else {
            showLoginPrompt()
        }
    }

    public fun showMenu() {
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
        val loginDialog = LoginDialog(project) { email, password ->
            handleLoginAttempt(email, password)
        }
        loginDialog.show()
    }

    private fun handleLoginAttempt(email: String, password: String) {
        updateStatusBar("connecting")
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
        updateStatusBar("disconnected")
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

    private fun connectToWebSocket(initialMessage: String? = null) {
        try {
            val client = ClientManager.createClient()
            val uri = URI("wss://ws.devek.dev")
            session = client.connectToServer(this, uri)
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
            updateStatusBar("connecting")
            Thread.sleep(reconnectInterval)
            connectToWebSocket()
        } else {
            updateStatusBar("error")
            Messages.showErrorDialog(
                project,
                "Failed to connect to Devek.dev server.",
                "Connection Error"
            )
        }
    }

    @OnMessage
    fun onMessage(message: String) {
        try {
            val response = Json.decodeFromString<WebSocketResponse>(message)
            when (response.type) {
                "init" -> {
                    updateStatusBar("connected")
                    reconnectAttempts = 0
                }
                "auth" -> {
                    if (response.status == "success") {
                        val token = response.token
                        if (token != null) {
                            saveToken(token)
                            updateStatusBar("connected")
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

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val document = editor.document

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (session?.isOpen != true || authToken == null) return

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
        })
    }

    @OnClose
    fun onClose(session: Session, reason: jakarta.websocket.CloseReason) {
        println("WebSocket closed: ${reason.reasonPhrase}")
        updateStatusBar("disconnected")
        handleReconnection()
    }

    private fun updateStatusBar(status: String) {
        DevekService.getInstance(project).updateStatus(status)
    }

    private inner class LoginDialog(
        project: Project,
        private val onLogin: (String, String) -> Unit
    ) : DialogWrapper(project) {
        private val emailField = JBTextField()
        private val passwordField = JBPasswordField()

        init {
            title = "Login to Devek.dev"
            init()
        }

        override fun createCenterPanel(): JComponent {
            return panel {
                row("Email:") { cell(emailField).focused() }
                row("Password:") { cell(passwordField) }
            }
        }

        override fun doOKAction() {
            onLogin(emailField.text, String(passwordField.password))
            super.doOKAction()
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
}