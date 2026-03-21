package com.base44.sdk

import com.base44.sdk.http.Base44HttpClient
import com.base44.sdk.modules.*

/**
 * Configuration for creating a Base44 client.
 */
data class CreateClientConfig(
    /** Your Base44 application ID. */
    val appId: String,
    /** Base44 server URL. Defaults to `https://base44.app`. */
    val serverUrl: String = "https://base44.app",
    /** Optional user authentication token. */
    val token: String? = null,
    /** Optional service-role token for elevated permissions. */
    val serviceToken: String? = null,
    /** Optional app base URL (for redirect links). */
    val appBaseUrl: String = "",
    /** Optional functions version header value. */
    val functionsVersion: String? = null,
    /** Additional HTTP headers sent with every request. */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * The main Base44 SDK client.
 *
 * Create a client with [createClient]:
 * ```kotlin
 * val base44 = createClient(CreateClientConfig(appId = "my-app-id"))
 *
 * // Authenticate
 * val response = base44.auth.loginViaEmailPassword("user@example.com", "password")
 *
 * // Use entities
 * val products = base44.entities["Products"].list()
 *
 * // Use service role
 * val allOrders = base44.asServiceRole.entities["Orders"].list()
 * ```
 */
class Base44Client internal constructor(
    private val http: Base44HttpClient,
    private val functionsHttp: Base44HttpClient,
    private val serviceHttp: Base44HttpClient?,
    private val serviceFunctionsHttp: Base44HttpClient?,
    private val config: CreateClientConfig,
) {
    // ── User-scoped modules ───────────────────────────────────────────────────

    /** Access and manipulate entities in your app. */
    val entities: EntitiesModule = EntitiesModule(http, config.appId)

    /** Authentication operations. */
    val auth: AuthModule = AuthModule(
        http = http,
        functionsHttp = functionsHttp,
        appId = config.appId,
        appBaseUrl = config.appBaseUrl,
        serverUrl = config.serverUrl,
    )

    /** Invoke custom backend functions. */
    val functions: FunctionsModule = FunctionsModule(functionsHttp, config.appId)

    /** Access built-in integrations (AI, storage, email). */
    val integrations: IntegrationsModule = IntegrationsModule(http, config.appId)

    /** Create and manage AI agent conversations. */
    val agents: AgentsModule = AgentsModule(http, config.appId)

    /** View application logs. */
    val appLogs: AppLogsModule = AppLogsModule(http, config.appId)

    /** Manage app users. */
    val users: UsersModule = UsersModule(http, config.appId)

    // ── Token ─────────────────────────────────────────────────────────────────

    /** Updates the authentication token used by all user-scoped modules. */
    fun setToken(token: String) {
        auth.setToken(token)
    }

    // ── Service role ──────────────────────────────────────────────────────────

    /**
     * Provides access to service-role modules with elevated permissions.
     *
     * Requires [CreateClientConfig.serviceToken] to be set.
     */
    val asServiceRole: ServiceRoleClient
        get() {
            checkNotNull(serviceHttp) {
                "Service token is required to use asServiceRole. Provide a serviceToken in CreateClientConfig."
            }
            checkNotNull(serviceFunctionsHttp)
            return ServiceRoleClient(serviceHttp, serviceFunctionsHttp, config.appId)
        }
}

/**
 * A client scoped to service-role (admin) permissions.
 */
class ServiceRoleClient internal constructor(
    http: Base44HttpClient,
    functionsHttp: Base44HttpClient,
    appId: String,
) {
    /** Access entities with admin permissions. */
    val entities: EntitiesModule = EntitiesModule(http, appId)
    /** Invoke functions with admin permissions. */
    val functions: FunctionsModule = FunctionsModule(functionsHttp, appId)
    /** Access integrations with admin permissions. */
    val integrations: IntegrationsModule = IntegrationsModule(http, appId)
    /** Manage agent conversations with admin permissions. */
    val agents: AgentsModule = AgentsModule(http, appId)
    /** View application logs with admin permissions. */
    val appLogs: AppLogsModule = AppLogsModule(http, appId)
}

// ── Factory ───────────────────────────────────────────────────────────────────

/**
 * Creates a Base44 client with the given configuration.
 *
 * ```kotlin
 * val base44 = createClient(CreateClientConfig(appId = "my-app-id"))
 * ```
 */
fun createClient(config: CreateClientConfig): Base44Client {
    val baseUrl = "${config.serverUrl}/api"
    val baseHeaders = config.headers
    val functionHeaders = if (config.functionsVersion != null)
        baseHeaders + mapOf("Base44-Functions-Version" to config.functionsVersion)
    else baseHeaders

    val http = Base44HttpClient(baseUrl, baseHeaders, config.token)
    val functionsHttp = Base44HttpClient(baseUrl, functionHeaders, config.token)

    val serviceHttp = config.serviceToken?.let {
        Base44HttpClient(baseUrl, baseHeaders, it)
    }
    val serviceFunctionsHttp = config.serviceToken?.let {
        Base44HttpClient(baseUrl, functionHeaders, it)
    }

    return Base44Client(
        http = http,
        functionsHttp = functionsHttp,
        serviceHttp = serviceHttp,
        serviceFunctionsHttp = serviceFunctionsHttp,
        config = config,
    )
}
