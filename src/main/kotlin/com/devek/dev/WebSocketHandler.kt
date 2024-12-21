package com.devek.dev

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import kotlinx.serialization.json.Json

@ClientEndpoint
class WebSocketHandler(
    private val project: Project,
    private val json: Json,
    private val onToken: (String) -> Unit,
    private val onConnectionStatus: (String) -> Unit,
    private val onShowWebview: () -> Unit,
    private val onResetConnection: () -> Unit,
    private val authToken: String?
) {
    @OnMessage
    fun onMessage(message: String) {
        try {
            val response = json.decodeFromString<WebSocketResponse>(message)
            println("Successfully decoded response: $response")

            when (response.type) {
                "init" -> {
                    println("Handling init response")
                    onConnectionStatus("connected")
                    onResetConnection()

                    // If we're already authenticated, show the webview
                    if (authToken != null) {
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
                else -> println("Unknown response type: ${response.type}")
            }
        } catch (e: Exception) {
            println("Error processing message: $message")
            e.printStackTrace()
        }
    }

    @OnOpen
    fun onOpen(session: Session) {
        println("WebSocket session opened")
        println("Session ID: ${session.id}")
        println("Session properties: ${session.requestParameterMap}")
    }

    @OnError
    fun onError(session: Session?, error: Throwable?) {
        println("WebSocket error occurred")
        println("Session: ${session?.id}")
        error?.printStackTrace()
    }

    @OnClose
    fun onClose(session: Session, reason: jakarta.websocket.CloseReason) {
        println("WebSocket closed")
        println("Session ID: ${session.id}")
        println("Close reason: ${reason.reasonPhrase}")
        println("Close code: ${reason.closeCode}")
        onConnectionStatus("disconnected")
    }
}