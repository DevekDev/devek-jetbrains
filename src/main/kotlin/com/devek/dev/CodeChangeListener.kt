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
import java.net.InetAddress
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@ClientEndpoint
class CodeChangeListener : EditorFactoryListener {

    private var session: Session? = null
    private val environment: String = determineEnvironment()
    private val computerName: String = determineComputerName()

    init {
        connectToWebSocket()
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

    private fun connectToWebSocket() {
        try {
            val client = ClientManager.createClient()
            val uri = URI("wss://ws.devek.dev") // Replace with your WebSocket server URL
            session = client.connectToServer(this, uri)
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
                // Capture accurate timestamp for this change
                val timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)

                // Extract details of the change
                val documentUri = editor.document // Original document reference
                val changeText = event.newFragment.toString()
                val startLine = document.getLineNumber(event.offset) // Calculate line of change start
                val startChar = event.offset - document.getLineStartOffset(startLine) // Char position in line
                val endLine = document.getLineNumber(event.offset + event.newLength)
                val endChar = (event.offset + event.newLength) - document.getLineStartOffset(endLine)

                // Prepare change data
                val changeData = """
                    {
                        "document_uri": "$documentUri",
                        "timestamp": "$timestamp",
                        "start_line": $startLine,
                        "start_character": $startChar,
                        "end_line": $endLine,
                        "end_character": $endChar,
                        "text": "$changeText",
                        "environment": "$environment",
                        "computer_name": "$computerName"
                    }
                """.trimIndent()

                // Send change data to WebSocket server
                sendChangeToServer(changeData)
            }
        })
    }

    private fun sendChangeToServer(data: String) {
        try {
            session?.basicRemote?.sendText(data)
            println("Sent to WebSocket server: $data")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to send data to WebSocket server.")
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        // Optional: Handle editor released events
    }

    @OnClose
    fun onClose(session: Session, reason: jakarta.websocket.CloseReason) {
        println("WebSocket closed: ${reason.reasonPhrase}")
    }
}
