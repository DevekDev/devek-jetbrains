package com.devek.dev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

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