package com.base44.sdk.modules

import com.base44.sdk.http.Base44HttpClient
import com.base44.sdk.models.LoginResponse
import kotlinx.serialization.json.*

/**
 * Provides authentication operations for your Base44 app.
 *
 * ```kotlin
 * val response = base44.auth.loginViaEmailPassword("user@example.com", "password")
 * base44.setToken(response.accessToken)
 * val me = base44.auth.me()
 * ```
 */
class AuthModule internal constructor(
    private val http: Base44HttpClient,
    private val functionsHttp: Base44HttpClient,
    private val appId: String,
    val appBaseUrl: String = "",
    val serverUrl: String = "https://base44.app",
    private val tokenStore: TokenStore = TokenStore(),
) {
    // ── Me ────────────────────────────────────────────────────────────────────

    /** Returns the currently authenticated user. */
    suspend fun me(): JsonObject = http.get("/apps/$appId/entities/User/me").jsonObject

    /** Updates the current user's profile. */
    suspend fun updateMe(body: JsonObject): JsonObject =
        http.put("/apps/$appId/entities/User/me", body).jsonObject

    // ── Token ─────────────────────────────────────────────────────────────────

    /**
     * Sets the authentication token for all subsequent requests.
     *
     * Token is stored in-memory. Callers should persist to secure storage (e.g. Keychain/DataStore).
     */
    fun setToken(token: String, saveToStore: Boolean = true) {
        if (token.isEmpty()) return
        http.setToken(token)
        functionsHttp.setToken(token)
        if (saveToStore) tokenStore.token = token
    }

    /** Clears the current authentication token. */
    fun logout() {
        http.clearToken()
        functionsHttp.clearToken()
        tokenStore.token = null
    }

    /** Returns `true` if the current token is valid. */
    suspend fun isAuthenticated(): Boolean = try {
        me(); true
    } catch (_: Exception) { false }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates with email and password.
     *
     * @return [LoginResponse] containing the access token and user.
     */
    suspend fun loginViaEmailPassword(
        email: String,
        password: String,
        turnstileToken: String? = null,
    ): LoginResponse {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            turnstileToken?.let { put("turnstile_token", it) }
        }
        val response = http.post("/apps/$appId/auth/login", body).jsonObject
        val accessToken = response["access_token"]?.jsonPrimitive?.content
            ?: throw Exception("No access_token in login response")
        setToken(accessToken)
        return LoginResponse(
            accessToken = accessToken,
            user = response["user"]?.jsonObject,
        )
    }

    /** Creates a new user account. */
    suspend fun register(
        email: String,
        password: String,
        turnstileToken: String? = null,
        referralCode: String? = null,
    ): JsonObject {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            turnstileToken?.let { put("turnstile_token", it) }
            referralCode?.let { put("referral_code", it) }
        }
        return http.post("/apps/$appId/auth/register", body).jsonObject
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    /** Sends an OTP login code to the user's email. */
    suspend fun loginViaOtp(email: String): JsonObject {
        val body = buildJsonObject { put("email", email) }
        return http.post("/apps/$appId/auth/resend-otp", body).jsonObject
    }

    /** Verifies an OTP code and returns a login response. */
    suspend fun verifyOtp(email: String, otpCode: String): LoginResponse {
        val body = buildJsonObject {
            put("email", email)
            put("otp_code", otpCode)
        }
        val response = http.post("/apps/$appId/auth/verify-otp", body).jsonObject
        val accessToken = response["access_token"]?.jsonPrimitive?.content
            ?: throw Exception("No access_token in OTP verify response")
        setToken(accessToken)
        return LoginResponse(accessToken = accessToken, user = response["user"]?.jsonObject)
    }

    /** Resends an OTP to the given email. */
    suspend fun resendOtp(email: String) {
        val body = buildJsonObject { put("email", email) }
        http.post("/apps/$appId/auth/resend-otp", body)
    }

    // ── Password reset ────────────────────────────────────────────────────────

    /** Requests a password-reset email. */
    suspend fun resetPassword(email: String) {
        val body = buildJsonObject { put("email", email) }
        http.post("/apps/$appId/auth/reset-password-request", body)
    }

    /** Completes a password reset using the token from the reset email. */
    suspend fun resetPasswordConfirm(resetToken: String, newPassword: String) {
        val body = buildJsonObject {
            put("reset_token", resetToken)
            put("new_password", newPassword)
        }
        http.post("/apps/$appId/auth/reset-password", body)
    }

    /** Changes the authenticated user's password. */
    suspend fun changePassword(userId: String, currentPassword: String, newPassword: String) {
        val body = buildJsonObject {
            put("user_id", userId)
            put("current_password", currentPassword)
            put("new_password", newPassword)
        }
        http.post("/apps/$appId/auth/change-password", body)
    }
}

/** Thread-safe in-memory token store. */
class TokenStore {
    @Volatile
    var token: String? = null
}
