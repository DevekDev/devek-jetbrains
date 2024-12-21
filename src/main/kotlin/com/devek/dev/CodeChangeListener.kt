package com.devek.dev

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger

@Service
class CodeChangeListener : EditorFactoryListener {
    private val logger = Logger.getInstance(javaClass)

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor ?: run {
            logger.warn("Editor is null in editorCreated event")
            return
        }

        val document = editor.document
        val project = editor.project ?: run {
            logger.warn("Project is null in editorCreated event")
            return
        }

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                try {
                    if (project.isDisposed) {
                        logger.debug("Project is disposed, skipping code change event")
                        return
                    }

                    val pluginService = DevekPluginService.getInstance(project)
                    if (!pluginService.isConnected()) {
                        logger.debug("Plugin service not connected, skipping code change event")
                        return
                    }

                    pluginService.sendCodeChange(event, document)
                } catch (e: Exception) {
                    logger.error("Error handling document change", e)
                }
            }
        })
    }
}