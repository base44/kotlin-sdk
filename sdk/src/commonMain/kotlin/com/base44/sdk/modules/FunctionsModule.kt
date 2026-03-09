package com.base44.sdk.modules

import com.base44.sdk.http.Base44HttpClient
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Invokes custom backend functions defined in your Base44 app.
 *
 * ```kotlin
 * val result = base44.functions.invoke("sendWelcomeEmail", buildJsonObject { put("userId", "abc") })
 * ```
 */
class FunctionsModule internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
) {
    /**
     * Invokes a backend function by name.
     *
     * @param functionName The name of the function to invoke.
     * @param data JSON body to pass to the function.
     * @return The function's response as a [JsonElement].
     */
    suspend fun invoke(functionName: String, data: JsonObject = JsonObject(emptyMap())): JsonElement {
        return http.post("/apps/$appId/functions/$functionName", data)
    }

    /**
     * Invokes a backend function with file uploads (multipart).
     *
     * @param functionName The name of the function to invoke.
     * @param fields Text fields.
     * @param files File fields: name → (bytes, filename, mimeType).
     */
    suspend fun invokeWithFiles(
        functionName: String,
        fields: Map<String, String> = emptyMap(),
        files: Map<String, Triple<ByteArray, String, String>> = emptyMap(),
    ): JsonElement {
        val formData = formData {
            fields.forEach { (name, value) -> append(name, value) }
            files.forEach { (name, triple) ->
                val (bytes, filename, mimeType) = triple
                append(name, bytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                })
            }
        }
        return http.postMultipart("/apps/$appId/functions/$functionName", formData)
    }
}
