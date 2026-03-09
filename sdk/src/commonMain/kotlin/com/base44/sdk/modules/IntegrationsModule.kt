package com.base44.sdk.modules

import com.base44.sdk.http.Base44HttpClient
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Provides access to Base44 built-in integrations (AI, storage, email, etc.).
 *
 * ```kotlin
 * // Invoke LLM
 * val result = base44.integrations.core.invokeLLM(prompt = "What is 2+2?")
 *
 * // Upload file
 * val uploaded = base44.integrations.core.uploadFile(bytes, "photo.jpg", "image/jpeg")
 *
 * // Generic
 * val response = base44.integrations.invoke("MyPackage", "MyEndpoint", buildJsonObject { put("param", "val") })
 * ```
 */
class IntegrationsModule internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
) {
    /** Core integration endpoints. */
    val core: CoreIntegrations get() = CoreIntegrations(http, appId)

    /**
     * Invokes any integration endpoint.
     *
     * @param packageName Integration package (e.g. `"Core"` or an installable package name).
     * @param endpointName Endpoint name (e.g. `"InvokeLLM"`).
     * @param data JSON body.
     */
    suspend fun invoke(
        packageName: String,
        endpointName: String,
        data: JsonObject = JsonObject(emptyMap()),
    ): JsonElement {
        val path = integrationPath(packageName, endpointName)
        return http.post(path, data)
    }

    /** Invokes an integration endpoint with file uploads. */
    suspend fun invokeWithFiles(
        packageName: String,
        endpointName: String,
        fields: Map<String, String> = emptyMap(),
        files: Map<String, Triple<ByteArray, String, String>> = emptyMap(),
    ): JsonElement {
        val path = integrationPath(packageName, endpointName)
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
        return http.postMultipart(path, formData)
    }

    private fun integrationPath(packageName: String, endpointName: String): String =
        if (packageName == "Core") {
            "/apps/$appId/integration-endpoints/Core/$endpointName"
        } else {
            "/apps/$appId/integration-endpoints/installable/$packageName/integration-endpoints/$endpointName"
        }
}

/** Typed wrappers for Base44 Core integration endpoints. */
class CoreIntegrations internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
) {
    private fun path(endpoint: String) = "/apps/$appId/integration-endpoints/Core/$endpoint"

    /** Invokes an LLM with a prompt. */
    suspend fun invokeLLM(
        prompt: String,
        systemPrompt: String? = null,
        model: String? = null,
        responseFormat: String? = null,
        imageUrl: String? = null,
    ): JsonElement {
        val body = buildJsonObject {
            put("prompt", prompt)
            systemPrompt?.let { put("systemPrompt", it) }
            model?.let { put("model", it) }
            responseFormat?.let { put("responseFormat", it) }
            imageUrl?.let { put("imageUrl", it) }
        }
        return http.post(path("InvokeLLM"), body)
    }

    /** Generates an image from a text prompt. */
    suspend fun generateImage(prompt: String, model: String? = null): JsonElement {
        val body = buildJsonObject {
            put("prompt", prompt)
            model?.let { put("model", it) }
        }
        return http.post(path("GenerateImage"), body)
    }

    /** Uploads a public file and returns the URL. */
    suspend fun uploadFile(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String = "application/octet-stream",
    ): JsonElement {
        val formData = formData {
            append("file", fileBytes, Headers.build {
                append(HttpHeaders.ContentType, mimeType)
                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
            })
        }
        return http.postMultipart(path("UploadFile"), formData)
    }

    /** Uploads a private file. */
    suspend fun uploadPrivateFile(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String = "application/octet-stream",
    ): JsonElement {
        val formData = formData {
            append("file", fileBytes, Headers.build {
                append(HttpHeaders.ContentType, mimeType)
                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
            })
        }
        return http.postMultipart(path("UploadPrivateFile"), formData)
    }

    /** Creates a signed URL for an existing private file. */
    suspend fun createFileSignedUrl(fileKey: String): JsonElement {
        val body = buildJsonObject { put("fileKey", fileKey) }
        return http.post(path("CreateFileSignedUrl"), body)
    }

    /** Sends an email. */
    suspend fun sendEmail(
        to: String,
        subject: String,
        body: String,
        from: String? = null,
    ): JsonElement {
        val payload = buildJsonObject {
            put("to", to)
            put("subject", subject)
            put("body", body)
            from?.let { put("from", it) }
        }
        return http.post(path("SendEmail"), payload)
    }

    /** Extracts structured data from an uploaded file. */
    suspend fun extractDataFromUploadedFile(fileUrl: String, extractionPrompt: String): JsonElement {
        val body = buildJsonObject {
            put("fileUrl", fileUrl)
            put("extractionPrompt", extractionPrompt)
        }
        return http.post(path("ExtractDataFromUploadedFile"), body)
    }
}
