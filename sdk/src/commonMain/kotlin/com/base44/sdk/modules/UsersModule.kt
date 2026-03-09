package com.base44.sdk.modules

import com.base44.sdk.http.Base44HttpClient
import kotlinx.serialization.json.*

/** Lists and manages users in your Base44 app. */
class UsersModule internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
) {
    /** Returns all users in the app. */
    suspend fun list(limit: Int? = null, skip: Int? = null): List<JsonObject> {
        val params = buildMap<String, String> {
            limit?.let { put("limit", it.toString()) }
            skip?.let { put("skip", it.toString()) }
        }
        return http.get("/apps/$appId/users", params).jsonArray.map { it.jsonObject }
    }

    /** Invites a user by email. */
    suspend fun inviteUser(email: String, role: String): JsonObject {
        val body = buildJsonObject {
            put("user_email", email)
            put("role", role)
        }
        return http.post("/apps/$appId/runtime/users/invite-user", body).jsonObject
    }
}
