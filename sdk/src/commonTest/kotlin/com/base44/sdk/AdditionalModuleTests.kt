package com.base44.sdk

import com.base44.sdk.http.Base44HttpClient
import com.base44.sdk.models.AgentConversation
import com.base44.sdk.models.AgentMessage
import com.base44.sdk.models.CreateConversationParams
import com.base44.sdk.modules.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

// ── IntegrationsModule Tests ──────────────────────────────────────────────────

class IntegrationsModuleTest {

    @Test
    fun testInvokeCoreEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/integration-endpoints/Core/InvokeLLM", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = ByteReadChannel("""{"result":"Paris"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val integrations = IntegrationsModule(http, "test-app")
        val result = integrations.invoke("Core", "InvokeLLM", buildJsonObject { put("prompt", "What is the capital of France?") })
        assertEquals("Paris", result.jsonObject["result"]?.jsonPrimitive?.content)
    }

    @Test
    fun testInvokeInstallablePackageEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals(
                "/api/apps/test-app/integration-endpoints/installable/Stripe/integration-endpoints/CreatePayment",
                request.url.encodedPath
            )
            respond(
                content = ByteReadChannel("""{"paymentId":"pay_123"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val integrations = IntegrationsModule(http, "test-app")
        val result = integrations.invoke("Stripe", "CreatePayment", buildJsonObject { put("amount", 1000) })
        assertEquals("pay_123", result.jsonObject["paymentId"]?.jsonPrimitive?.content)
    }

    @Test
    fun testCoreInvokeLLM() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/integration-endpoints/Core/InvokeLLM", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("Hello", json["prompt"]?.jsonPrimitive?.content)
            respond(
                content = ByteReadChannel("""{"text":"Hi there"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val core = CoreIntegrations(http, "test-app")
        val result = core.invokeLLM(prompt = "Hello")
        assertEquals("Hi there", result.jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun testCoreGenerateImage() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/integration-endpoints/Core/GenerateImage", request.url.encodedPath)
            respond(
                content = ByteReadChannel("""{"url":"https://example.com/image.png"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val core = CoreIntegrations(http, "test-app")
        val result = core.generateImage(prompt = "A sunset")
        assertEquals("https://example.com/image.png", result.jsonObject["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun testCoreSendEmail() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/integration-endpoints/Core/SendEmail", request.url.encodedPath)
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals("user@example.com", body["to"]?.jsonPrimitive?.content)
            assertEquals("Hello", body["subject"]?.jsonPrimitive?.content)
            respond(
                content = ByteReadChannel("""{"sent":true}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val core = CoreIntegrations(http, "test-app")
        val result = core.sendEmail(to = "user@example.com", subject = "Hello", body = "Hi!")
        assertEquals(true, result.jsonObject["sent"]?.jsonPrimitive?.booleanOrNull)
    }
}

// ── AgentsModule Tests ────────────────────────────────────────────────────────

class AgentsModuleTest {

    @Test
    fun testCreateConversation() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/agents/conversations", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = ByteReadChannel("""{"id":"conv-1","agent_name":"SupportBot"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val agents = AgentsModule(http, "test-app")
        val conv = agents.createConversation(CreateConversationParams(agentName = "SupportBot"))
        assertEquals("conv-1", conv.id)
        assertEquals("SupportBot", conv.agentName)
    }

    @Test
    fun testGetConversation() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/agents/conversations/conv-1", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel("""{"id":"conv-1","messages":[{"id":"msg-1","role":"user","content":"Hello"}]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val agents = AgentsModule(http, "test-app")
        val conv = agents.getConversation("conv-1")
        assertEquals("conv-1", conv.id)
        assertEquals(1, conv.messages?.size)
        assertEquals("Hello", conv.messages?.firstOrNull()?.content)
    }

    @Test
    fun testGetConversations() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/agents/conversations", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel("""[{"id":"conv-1"},{"id":"conv-2"}]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val agents = AgentsModule(http, "test-app")
        val convs = agents.getConversations()
        assertEquals(2, convs.size)
        assertEquals("conv-1", convs[0].id)
    }

    @Test
    fun testAddMessage() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/agents/conversations/v2/conv-1/messages", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = ByteReadChannel("""{"id":"msg-2","role":"user","content":"Help me"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val agents = AgentsModule(http, "test-app")
        val conv = AgentConversation(id = "conv-1")
        val msg = agents.addMessage(conv, AgentMessage(role = "user", content = "Help me"))
        assertEquals("msg-2", msg.id)
        assertEquals("user", msg.role)
    }

    @Test
    fun testAgentPathsCorrect() {
        val appId = "test-app"
        assertEquals("/apps/test-app/agents/conversations", "/apps/$appId/agents/conversations")
        assertEquals("/apps/test-app/agents/conversations/v2/conv-1/messages",
            "/apps/$appId/agents/conversations/v2/conv-1/messages")
    }
}

// ── AppLogsModule Tests ───────────────────────────────────────────────────────

class AppLogsModuleTest {

    @Test
    fun testListCallsCorrectEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/app-logs/test-app", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel("""[{"id":"log-1","message":"Event"}]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val appLogs = AppLogsModule(http, "test-app")
        val logs = appLogs.list()
        assertEquals(1, logs.size)
        assertEquals("Event", logs[0]["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun testListSendsPaginationParams() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("50", request.url.parameters["limit"])
            assertEquals("10", request.url.parameters["skip"])
            respond(
                content = ByteReadChannel("""[]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val appLogs = AppLogsModule(http, "test-app")
        appLogs.list(limit = 50, skip = 10)
    }

    @Test
    fun testAppLogsPath() {
        val appId = "my-app"
        assertEquals("/app-logs/my-app", "/app-logs/$appId")
    }
}

// ── UsersModule Tests ─────────────────────────────────────────────────────────

class UsersModuleTest {

    @Test
    fun testListCallsCorrectEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/users", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel("""[{"id":"user-1","email":"alice@example.com"}]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val users = UsersModule(http, "test-app")
        val result = users.list()
        assertEquals(1, result.size)
        assertEquals("alice@example.com", result[0]["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun testInviteUserCallsRuntimeEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/apps/test-app/runtime/users/invite-user", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals("bob@example.com", body["user_email"]?.jsonPrimitive?.content)
            assertEquals("member", body["role"]?.jsonPrimitive?.content)
            respond(
                content = ByteReadChannel("""{"invited":true}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val users = UsersModule(http, "test-app")
        val result = users.inviteUser("bob@example.com", "member")
        assertEquals(true, result["invited"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun testListSendsPaginationParams() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("25", request.url.parameters["limit"])
            assertEquals("5", request.url.parameters["skip"])
            respond(
                content = ByteReadChannel("""[]"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = Base44HttpClient("https://base44.app/api", httpClient = HttpClient(mockEngine))
        val users = UsersModule(http, "test-app")
        users.list(limit = 25, skip = 5)
    }
}

// Helper to read body bytes from mock engine request
private suspend fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray =
    when (this) {
        is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
        else -> ByteArray(0)
    }
