package com.devek.dev

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import jakarta.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.glassfish.tyrus.client.ClientManager
import java.net.URI
import kotlin.random.Random

@ClientEndpoint
class WebSocketHandler(
    private val project: Project,
    private val json: Json,
    private val onToken: (String) -> Unit,
    private val onConnectionStatus: (String) -> Unit,
    private val onShowWebview: () -> Unit,
    private val getAuthToken: () -> String?,
    private val onSessionUpdated: (Session?) -> Unit
) {
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentSession: Session? = null
    private val client = ClientManager.createClient()
    private val serverUri = URI("wss://ws.devek.dev")


    fun connect(initialMessage: String? = null) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Use structured concurrency with coroutineScope
                    coroutineScope {
                        currentSession = client.connectToServer(this@WebSocketHandler, serverUri)
                        onSessionUpdated(currentSession)
                        println("Connected to WebSocket server.")

                        val authToken = getAuthToken()
                        when {
                            authToken != null -> sendAuthToken(authToken)
                            initialMessage != null -> currentSession?.basicRemote?.sendText(initialMessage)
                            else -> {

                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Failed to connect to WebSocket server: ${e.message}")
                e.printStackTrace()
                scheduleReconnect()
            }
        }
    }

    private fun sendAuthToken(token: String) {
        val authRequest = AuthRequest(token = token)
        val authMessage = json.encodeToString(AuthRequest.serializer(), authRequest)
        currentSession?.basicRemote?.sendText(authMessage)
    }

    @OnOpen
    fun onOpen(session: Session) {
        println("WebSocket connection established")
        scope.launch(Dispatchers.IO) {
            onConnectionStatus("connected")
            reconnectJob?.cancel()
        }
    }

    @OnClose
    fun onClose(session: Session, reason: CloseReason) {
        println("WebSocket connection closed: ${reason.reasonPhrase}")
        scope.launch(Dispatchers.IO) {
            currentSession = null
            onSessionUpdated(null)
            onConnectionStatus("disconnected")
            scheduleReconnect()
        }
    }

    @OnMessage
    fun onMessage(message: String) {
        try {
            val response = json.decodeFromString<WebSocketResponse>(message)
            when (response.type) {
                "init" -> {
                    println("Handling init response")
                    onConnectionStatus("connected")

                    // If we're already authenticated, show the webview
                    if (getAuthToken() != null) {
                        ApplicationManager.getApplication().invokeLater {
                            onShowWebview()
                        }
                    }
                }
                "auth", "login" -> {
                    println("Handling auth/login response")
                    if (response.status == "success") {
                        println("Login successful, token present: ${response.token != null}")
                        val token = response.token
                        if (token != null) {
                            println("Saving token and updating UI")
                            onToken(token)
                            onConnectionStatus("connected")

                            // Update UI on the EDT
                            ApplicationManager.getApplication().invokeLater {
                                println("Updating UI on EDT")
                                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Devek.dev")
                                toolWindow?.let { tw ->
                                    println("Found tool window, updating content")
                                    tw.hide()
                                    onShowWebview()
                                } ?: println("Tool window not found")
                            }
                        } else {
                            println("Token was null in success response")
                        }
                    } else {
                        println("Login failed: ${response.message}")
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                response.message ?: "Login failed",
                                "Login Error"
                            )
                        }
                    }
                }
                "change" -> println("Change response - ${response.status}")
                else -> println("Unknown response type: ${response.type}")
            }
        } catch (e: Exception) {
            println("Error processing WebSocket message: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            var attempts = 0
            val maxAttempts = 5
            val baseDelay = 5000L

            while (attempts < maxAttempts) {
                delay(calculateBackoffDelay(attempts, baseDelay))
                attempts++

                try {
                    onConnectionStatus("connecting")
                    supervisorScope {
                        connect()
                    }
                    break
                } catch (e: Exception) {
                    println("Reconnection attempt $attempts failed: ${e.message}")
                    if (attempts >= maxAttempts) {
                        onConnectionStatus("error")
                        withContext(Dispatchers.Main) {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    "Failed to reconnect to Devek.dev server after $maxAttempts attempts.",
                                    "Connection Error"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun calculateBackoffDelay(attempt: Int, baseDelay: Long): Long {
        val exponentialDelay = baseDelay * (1L shl attempt.coerceAtMost(6))
        val jitter = Random.nextLong(exponentialDelay / 4)
        return (exponentialDelay + jitter).coerceAtMost(300000)
    }

    fun dispose() {
        currentSession?.close()
        reconnectJob?.cancel()
        scope.cancel()
    }
}