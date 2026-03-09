package com.base44.sdk.http

import com.base44.sdk.models.Base44Exception
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

/**
 * Internal HTTP client wrapper around ktor, shared by all SDK modules.
 *
 * Thread-safe: token updates are propagated via a volatile field.
 */
internal class Base44HttpClient(
    val baseUrl: String,
    initialHeaders: Map<String, String> = emptyMap(),
    initialToken: String? = null,
    private val httpClient: HttpClient = buildDefaultHttpClient(),
) {
    @Volatile
    private var token: String? = initialToken
    private val extraHeaders: Map<String, String> = initialHeaders

    fun setToken(newToken: String) {
        token = newToken
    }

    fun clearToken() {
        token = null
    }

    fun currentToken(): String? = token

    // ── GET ──────────────────────────────────────────────────────────────────

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonElement {
        val response = httpClient.get("$baseUrl$path") {
            applyHeaders()
            params.forEach { (k, v) -> parameter(k, v) }
        }
        return response.checkedBody()
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    suspend fun post(path: String, body: JsonElement = JsonObject(emptyMap())): JsonElement {
        val response = httpClient.post("$baseUrl$path") {
            applyHeaders()
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return response.checkedBody()
    }

    suspend fun postMultipart(path: String, formData: List<PartData>): JsonElement {
        val response = httpClient.submitFormWithBinaryData("$baseUrl$path", formData) {
            applyHeaders()
        }
        return response.checkedBody()
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    suspend fun put(path: String, body: JsonElement = JsonObject(emptyMap())): JsonElement {
        val response = httpClient.put("$baseUrl$path") {
            applyHeaders()
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return response.checkedBody()
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    suspend fun delete(path: String, body: JsonElement? = null): JsonElement {
        val response = httpClient.delete("$baseUrl$path") {
            applyHeaders()
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        return response.checkedBody()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun HttpRequestBuilder.applyHeaders() {
        extraHeaders.forEach { (k, v) -> header(k, v) }
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun HttpResponse.checkedBody(): JsonElement {
        val text = bodyAsText()
        if (status.value !in 200..299) {
            val errorJson = try { Json.parseToJsonElement(text).jsonObject } catch (_: Exception) { null }
            val message = errorJson?.get("message")?.jsonPrimitive?.contentOrNull
                ?: errorJson?.get("detail")?.jsonPrimitive?.contentOrNull
                ?: status.description
            val code = errorJson?.get("code")?.jsonPrimitive?.contentOrNull
            throw Base44Exception(status = status.value, message = message, code = code, data = errorJson)
        }
        return try { Json.parseToJsonElement(text) } catch (_: Exception) { JsonObject(emptyMap()) }
    }
}

internal fun buildDefaultHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
    }
}
