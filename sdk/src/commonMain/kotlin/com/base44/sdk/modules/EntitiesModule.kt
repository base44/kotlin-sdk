package com.base44.sdk.modules

import com.base44.sdk.http.Base44HttpClient
import com.base44.sdk.models.DeleteManyResult
import com.base44.sdk.models.DeleteResult
import com.base44.sdk.models.ImportResult
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Provides dynamic access to all entity types in your Base44 app.
 *
 * Use `operator fun get(entityName: String)` to access an entity handler:
 * ```kotlin
 * val products = base44.entities["Products"].list()
 * val order = base44.entities["Orders"].get("order-id")
 * ```
 */
class EntitiesModule internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
) {
    /** Access entity operations for a given entity name. */
    operator fun get(entityName: String): EntityHandler = EntityHandler(http, appId, entityName)
}

/**
 * Provides CRUD and query operations for a specific entity type.
 */
class EntityHandler internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
    private val entityName: String,
) {
    private val basePath get() = "/apps/$appId/entities/$entityName"

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * Lists entities with optional sorting, pagination, and field selection.
     *
     * @param sort Sort field; prefix with `-` for descending (e.g. `"-created_at"`).
     * @param limit Maximum number of results.
     * @param skip Number of results to skip.
     * @param fields Specific fields to return.
     * @return List of entity JSON objects.
     */
    suspend fun list(
        sort: String? = null,
        limit: Int? = null,
        skip: Int? = null,
        fields: List<String>? = null,
    ): List<JsonObject> {
        val params = buildParams(sort = sort, limit = limit, skip = skip, fields = fields)
        return http.get(basePath, params).jsonArray.map { it.jsonObject }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    /**
     * Filters entities by a query object.
     *
     * @param query Map of field/value pairs to match.
     */
    suspend fun filter(
        query: JsonObject,
        sort: String? = null,
        limit: Int? = null,
        skip: Int? = null,
        fields: List<String>? = null,
    ): List<JsonObject> {
        val params = buildParams(sort = sort, limit = limit, skip = skip, fields = fields).toMutableMap()
        params["q"] = query.toString()
        return http.get(basePath, params).jsonArray.map { it.jsonObject }
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    /** Gets a single entity by ID. */
    suspend fun get(id: String): JsonObject = http.get("$basePath/$id").jsonObject

    // ── Create ────────────────────────────────────────────────────────────────

    /** Creates a new entity. */
    suspend fun create(body: JsonObject): JsonObject = http.post(basePath, body).jsonObject

    // ── Update ────────────────────────────────────────────────────────────────

    /** Updates an entity by ID. */
    suspend fun update(id: String, body: JsonObject): JsonObject = http.put("$basePath/$id", body).jsonObject

    // ── Delete ────────────────────────────────────────────────────────────────

    /** Deletes an entity by ID. */
    suspend fun delete(id: String): DeleteResult {
        val json = http.delete("$basePath/$id").jsonObject
        return DeleteResult(
            id = json["id"]?.jsonPrimitive?.contentOrNull,
            deleted = json["deleted"]?.jsonPrimitive?.booleanOrNull,
        )
    }

    /** Deletes all entities matching a query. */
    suspend fun deleteMany(query: JsonObject): DeleteManyResult {
        val json = http.delete(basePath, query).jsonObject
        return DeleteManyResult(deleted = json["deleted"]?.jsonPrimitive?.intOrNull)
    }

    // ── Bulk Create ───────────────────────────────────────────────────────────

    /** Creates multiple entities in a single request. */
    suspend fun bulkCreate(bodies: List<JsonObject>): List<JsonObject> {
        val json = http.post("$basePath/bulk", JsonArray(bodies)).jsonArray
        return json.map { it.jsonObject }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /** Imports entities from CSV/Excel file bytes. */
    suspend fun importEntities(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String = "text/csv",
    ): ImportResult {
        val formData = formData {
            append("file", fileBytes, Headers.build {
                append(HttpHeaders.ContentType, mimeType)
                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
            })
        }
        val json = http.postMultipart("$basePath/import", formData).jsonObject
        return ImportResult(
            created = json["created"]?.jsonPrimitive?.intOrNull,
            updated = json["updated"]?.jsonPrimitive?.intOrNull,
            errors = json["errors"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildParams(
        sort: String?,
        limit: Int?,
        skip: Int?,
        fields: List<String>?,
    ): Map<String, String> = buildMap {
        sort?.let { put("sort", it) }
        limit?.let { put("limit", it.toString()) }
        skip?.let { put("skip", it.toString()) }
        fields?.let { put("fields", it.joinToString(",")) }
    }
}
