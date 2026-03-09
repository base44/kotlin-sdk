package com.base44.sdk.modules

import com.base44.sdk.http.Base44HttpClient
import kotlinx.serialization.json.*

/** Lists application log entries from your Base44 app. */
class AppLogsModule internal constructor(
    private val http: Base44HttpClient,
    private val appId: String,
) {
    /** Returns log entries for the app. */
    suspend fun list(limit: Int? = null, skip: Int? = null): List<JsonObject> {
        val params = buildMap<String, String> {
            limit?.let { put("limit", it.toString()) }
            skip?.let { put("skip", it.toString()) }
        }
        return http.get("/app-logs/$appId", params).jsonArray.map { it.jsonObject }
    }
}
