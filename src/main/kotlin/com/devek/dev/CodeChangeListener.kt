package com.devek.dev

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project

class CodeChangeListener(private val project: Project) : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val document = editor.document

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val pluginService = DevekPluginService.getInstance(project)
                if (!pluginService.isConnected()) return

                pluginService.sendCodeChange(event, document)
            }
        })
    }
}