package com.devek.dev

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import java.awt.BorderLayout
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.labels.LinkLabel
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton

class DevekToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(
            LoginPanel(project),
            "",
            false
        )
        contentManager.addContent(content)
    }
}

private class LoginPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val emailField = JBTextField()
    private val passwordField = JBPasswordField()

    init {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

            // Icon and Title
            add(JBLabel(AllIcons.General.User).apply {
                alignmentX = CENTER_ALIGNMENT
            })
            add(Box.createVerticalStrut(10))

            // Title
            add(JBLabel("Sign in to Devek.dev").apply {
                font = font.deriveFont(font.size + 2f)
                alignmentX = CENTER_ALIGNMENT
            })
            add(Box.createVerticalStrut(5))

            // Subtitle
            add(JBLabel("Track and analyze your code changes").apply {
                alignmentX = CENTER_ALIGNMENT
            })
            add(Box.createVerticalStrut(20))

            // Form Panel
            add(JPanel(GridBagLayout()).apply {
                val gbc = GridBagConstraints().apply {
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(5, 5, 5, 5)
                    weightx = 1.0
                }

                // Email
                gbc.gridy = 0
                add(JBLabel("Email"), gbc)
                gbc.gridy = 1
                add(emailField, gbc)

                // Password
                gbc.gridy = 2
                add(JBLabel("Password"), gbc)
                gbc.gridy = 3
                add(passwordField, gbc)

                // Login Button
                gbc.gridy = 4
                gbc.insets = Insets(15, 5, 5, 5)
                add(JButton("Login").apply {
                    addActionListener {
                        val pluginService = DevekPluginService.getInstance(project)
                        pluginService.handleLoginAttempt(emailField.text, String(passwordField.password))
                    }
                }, gbc)
            })
            add(Box.createVerticalStrut(15))

            // Sign up link
            add(LinkLabel<String>("Don't have an account? Sign up", null).apply {
                alignmentX = CENTER_ALIGNMENT
                setListener({ _, _ -> BrowserUtil.browse("https://devek.dev") }, null)
            })
        }

        add(mainPanel, BorderLayout.NORTH)
    }
}