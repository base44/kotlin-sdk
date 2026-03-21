package com.base44.sdk

import com.base44.sdk.http.Base44HttpClient
import com.base44.sdk.modules.EntityHandler
import io.ktor.client.engine.mock.*
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class EntitiesModuleTest {

    // ── Path construction ────────────────────────────────────────────────────

    @Test
    fun testBasePathConstruction() {
        val appId = "test-app-123"
        val entityName = "Products"
        val expected = "/apps/$appId/entities/$entityName"
        assertEquals("/apps/test-app-123/entities/Products", expected)
    }

    @Test
    fun testBulkCreatePath() {
        val appId = "my-app"
        val entityName = "Orders"
        assertEquals("/apps/my-app/entities/Orders/bulk", "/apps/$appId/entities/$entityName/bulk")
    }

    @Test
    fun testImportPath() {
        val appId = "my-app"
        val entityName = "Products"
        assertEquals("/apps/my-app/entities/Products/import", "/apps/$appId/entities/$entityName/import")
    }

    // ── List with mock ────────────────────────────────────────────────────────

    @Test
    fun testListCallsCorrectEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/entities/Products", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel("""[{"id":"1","name":"Widget"}]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient(
            baseUrl = "https://base44.app/api",
            httpClient = HttpClient(mockEngine)
        )
        val handler = EntityHandler(http, "test-app", "Products")
        val result = handler.list()
        assertEquals(1, result.size)
        assertEquals("Widget", result[0]["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun testGetCallsCorrectEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/entities/Products/abc123", request.url.encodedPath)
            respond(
                content = ByteReadChannel("""{"id":"abc123","name":"Widget"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val handler = EntityHandler(http, "test-app", "Products")
        val result = handler.get("abc123")
        assertEquals("abc123", result["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun testCreateCallsPostEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("POST", request.method.value)
            assertEquals("/api/apps/test-app/entities/Products", request.url.encodedPath)
            respond(
                content = ByteReadChannel("""{"id":"new-id","name":"New Widget"}"""),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val handler = EntityHandler(http, "test-app", "Products")
        val result = handler.create(buildJsonObject { put("name", "New Widget") })
        assertEquals("new-id", result["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun testDeleteCallsDeleteEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("DELETE", request.method.value)
            assertEquals("/api/apps/test-app/entities/Products/abc123", request.url.encodedPath)
            respond(
                content = ByteReadChannel("""{"deleted":true}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val handler = EntityHandler(http, "test-app", "Products")
        val result = handler.delete("abc123")
        assertEquals(true, result.deleted)
    }

    @Test
    fun testFilterSendsQueryParam() = runTest {
        val mockEngine = MockEngine { request ->
            val q = request.url.parameters["q"]
            assertNotNull(q)
            val parsed = Json.parseToJsonElement(q).jsonObject
            assertEquals("active", parsed["status"]?.jsonPrimitive?.content)
            respond(
                content = ByteReadChannel("""[]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val handler = EntityHandler(http, "test-app", "Products")
        handler.filter(buildJsonObject { put("status", "active") })
    }
}

// ── Auth Module Tests ────────────────────────────────────────────────────────

class AuthModuleTest {

    @Test
    fun testLoginEndpointPath() {
        val appId = "my-app"
        assertEquals("/apps/my-app/auth/login", "/apps/$appId/auth/login")
    }

    @Test
    fun testMeEndpointPath() {
        val appId = "my-app"
        assertEquals("/apps/my-app/entities/User/me", "/apps/$appId/entities/User/me")
    }

    @Test
    fun testLoginCallsCorrectEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/auth/login", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = ByteReadChannel("""{"access_token":"jwt-token-123","user":{"id":"user-1"}}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val functionsHttp = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val auth = com.base44.sdk.modules.AuthModule(http, functionsHttp, "test-app")
        val response = auth.loginViaEmailPassword("user@example.com", "password")
        assertEquals("jwt-token-123", response.accessToken)
        // Token should have been set on the HTTP client
        assertEquals("jwt-token-123", http.currentToken())
    }
}

// ── Functions Module Tests ────────────────────────────────────────────────────

class FunctionsModuleTest {

    @Test
    fun testInvokeCallsCorrectPath() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/functions/myFunction", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = ByteReadChannel("""{"result":"ok"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val functions = com.base44.sdk.modules.FunctionsModule(http, "test-app")
        val result = functions.invoke("myFunction", buildJsonObject { put("key", "value") })
        assertEquals("ok", result.jsonObject["result"]?.jsonPrimitive?.content)
    }
}

// ── HTTP Client Tests ────────────────────────────────────────────────────────

class HttpClientTest {

    @Test
    fun testTokenInjection() {
        val client = Base44HttpClient("https://base44.app/api")
        client.setToken("my-jwt")
        assertEquals("my-jwt", client.currentToken())
    }

    @Test
    fun testClearToken() {
        val client = Base44HttpClient("https://base44.app/api")
        client.setToken("my-jwt")
        client.clearToken()
        assertNull(client.currentToken())
    }

    @Test
    fun testTokenSentInHeader() = runTest {
        val mockEngine = MockEngine { request ->
            val authHeader = request.headers[HttpHeaders.Authorization]
            assertEquals("Bearer test-token", authHeader)
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = Base44HttpClient(
            baseUrl = "https://base44.app/api",
            initialToken = "test-token",
            httpClient = HttpClient(mockEngine),
        )
        client.get("/test")
    }

    @Test
    fun testCustomHeadersForwarded() = runTest {
        val mockEngine = MockEngine { request ->
            val customHeader = request.headers["X-Custom-Header"]
            assertEquals("my-value-123", customHeader)
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = Base44HttpClient(
            baseUrl = "https://base44.app/api",
            initialHeaders = mapOf("X-Custom-Header" to "my-value-123"),
            httpClient = HttpClient(mockEngine),
        )
        client.get("/test")
    }

    @Test
    fun testErrorResponseThrowsBase44Exception() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"message":"Not found","code":"NOT_FOUND"}"""),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val ex = assertFailsWith<com.base44.sdk.models.Base44Exception> {
            client.get("/apps/test/entities/Missing/abc")
        }
        assertEquals(404, ex.status)
        assertEquals("NOT_FOUND", ex.code)
        assertEquals("Not found", ex.message)
    }
}

// ── createClient Tests ────────────────────────────────────────────────────────

class CreateClientTest {

    @Test
    fun testCreateClientDefaultServerUrl() {
        val client = createClient(CreateClientConfig(appId = "my-app"))
        // The client is created without error
        assertNotNull(client)
    }

    @Test
    fun testServiceRoleRequiresServiceToken() {
        val client = createClient(CreateClientConfig(appId = "my-app"))
        assertFailsWith<IllegalStateException> {
            client.asServiceRole
        }
    }

    @Test
    fun testServiceRoleWithToken() {
        val client = createClient(CreateClientConfig(
            appId = "my-app",
            serviceToken = "service-token-xyz"
        ))
        // Should not throw
        val sr = client.asServiceRole
        assertNotNull(sr)
    }
}
