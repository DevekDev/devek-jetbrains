package com.devek.dev

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
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
import java.awt.event.KeyEvent
import java.awt.event.KeyAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.AnimatedIcon
import javax.swing.SwingConstants
import com.intellij.ui.components.panels.Wrapper

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
    private val loginButton = JButton("Login")
    private val loadingIcon = JBLabel(AnimatedIcon.Default())
    private val loadingLabel = JBLabel("Signing in...", SwingConstants.CENTER)
    private val loadingPanel = JPanel(BorderLayout(5, 0)).apply {
        add(loadingIcon, BorderLayout.WEST)
        add(loadingLabel, BorderLayout.CENTER)
        isVisible = false
    }

    init {
        setupUI()
        setupLoginActions()
        setupLoadingState()
    }

    private fun setupUI() {
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
            add(JBLabel("Optimize Your Development Productivity").apply {
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

                // Loading icon and label
                gbc.gridy = 4
                gbc.insets = Insets(15, 5, 5, 5)
                add(loadingPanel, gbc)

                // Login Button
                gbc.gridy = 5
                add(loginButton, gbc)
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

    private fun setupLoginActions() {
        // Handle login button click
        loginButton.addActionListener {
            performLogin()
        }

        // Handle Enter key in email field
        emailField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    if (emailField.text.isNotEmpty()) {
                        passwordField.requestFocus()
                    }
                }
            }
        })

        // Handle Enter key in password field
        passwordField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    performLogin()
                }
            }
        })
    }

    private fun setupLoadingState() {
        // Initialize loading state observers
        val pluginService = DevekPluginService.getInstance(project)
        pluginService.addStatusListener { status ->
            ApplicationManager.getApplication().invokeLater {
                when (status) {
                    "connecting" -> setLoading(true)
                    else -> setLoading(false)
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        emailField.isEnabled = !isLoading
        passwordField.isEnabled = !isLoading
        loginButton.isEnabled = !isLoading
        loadingPanel.isVisible = isLoading
    }

    private fun performLogin() {
        val pluginService = DevekPluginService.getInstance(project)
        pluginService.handleLoginAttempt(emailField.text, String(passwordField.password))
    }
}