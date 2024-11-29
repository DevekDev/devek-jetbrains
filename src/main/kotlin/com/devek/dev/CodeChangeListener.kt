package com.devek.dev

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.OnClose
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import org.glassfish.tyrus.client.ClientManager
import java.net.URI
import java.time.LocalDateTime

@ClientEndpoint
class CodeChangeListener : EditorFactoryListener {
    private var webSocketSession: Session? = null
    private val environment: String = determineEnvironment()

    init {
        connectToWebSocket()
    }

    private fun determineEnvironment(): String {
        return System.getProperty("idea.platform.prefix") ?: "Unknown"
    }

    private fun connectToWebSocket() {
        try {
            val client = ClientManager.createClient() // Use Tyrus client manager
            val uri = URI("ws://localhost:8080") // WebSocket server URL
            webSocketSession = client.connectToServer(this, uri)
            println("Connected to WebSocket server.")
        } catch (e: Exception) {
            println("Failed to connect to WebSocket server.")
            e.printStackTrace()
        }
    }

    @OnMessage
    fun onMessage(message: String) {
        println("Received from server: $message")
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val document = editor.document

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val changes = event.newFragment.toString()
                val timestamp = LocalDateTime.now().toString()
                val startOffset = event.offset
                val endOffset = event.offset + event.newLength

                val startLine = document.getLineNumber(startOffset)
                val endLine = document.getLineNumber(endOffset)

                val startCharacter = startOffset - document.getLineStartOffset(startLine)
                val endCharacter = endOffset - document.getLineStartOffset(endLine)

                val changeData = mapOf(
                    "document_uri" to editor.document.toString(),
                    "timestamp" to timestamp,
                    "start_line" to startLine,
                    "start_character" to startCharacter,
                    "end_line" to endLine,
                    "end_character" to endCharacter,
                    "text" to changes,
                    "computer_name" to (System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "Unknown"),
                    "environment" to environment
                )

                sendChangeToWebSocket(changeData)
            }
        })
    }

    private fun sendChangeToWebSocket(changeData: Map<String, Any>) {
        try {
            val jsonMessage = changeData.entries.joinToString(",", "{", "}") {
                "\"${it.key}\":\"${it.value}\""
            }
            webSocketSession?.basicRemote?.sendText(jsonMessage)
            println("Sent to WebSocket server: $jsonMessage")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to send message to WebSocket server.")
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        // Optional: Handle editor released events
    }

//    @OnClose
//    fun onClose(session: Session, reason: CloseReason) {
//        println("WebSocket closed: ${reason.reasonPhrase}")
//    }
}
