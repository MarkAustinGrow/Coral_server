package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.coralprotocol.coralserver.session.SessionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Data classes for the web interface API
 */
@Serializable
data class AgentInfo(
    val id: String,
    val name: String,
    val description: String = ""
)

@Serializable
data class ThreadInfo(
    val id: String,
    val name: String,
    val creatorId: String,
    val participants: List<String>,
    val messageCount: Int,
    val isClosed: Boolean,
    val summary: String? = null
)

@Serializable
data class MessageInfo(
    val id: String,
    val threadId: String,
    val senderId: String,
    val content: String,
    val mentions: List<String>,
    val timestamp: Long
)

@Serializable
data class SessionInfo(
    val id: String,
    val applicationId: String,
    val agentCount: Int,
    val threadCount: Int
)

@Serializable
data class DashboardInfo(
    val sessions: List<SessionInfo>,
    val totalAgents: Int,
    val totalThreads: Int,
    val totalMessages: Int
)

/**
 * Configures routes for the web interface.
 */
fun Routing.webInterfaceRoutes(servers: ConcurrentMap<String, Server>) {
    // Serve the web interface
    get("/dashboard") {
        call.respondText(
            this::class.java.classLoader.getResource("web/index.html")?.readText() ?: "Resource not found",
            ContentType.Text.Html
        )
    }
    
    // Serve static files
    get("/static/{filename}") {
        val filename = call.parameters["filename"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing filename")
        val resource = this::class.java.classLoader.getResource("web/static/$filename")
        
        if (resource != null) {
            val contentType = when {
                filename.endsWith(".css") -> ContentType.Text.CSS
                filename.endsWith(".js") -> ContentType.Text.JavaScript
                filename.endsWith(".png") -> ContentType.Image.PNG
                filename.endsWith(".jpg") -> ContentType.Image.JPEG
                filename.endsWith(".svg") -> ContentType.Image.SVG
                else -> ContentType.Application.OctetStream
            }
            call.respondText(resource.readText(), contentType)
        } else {
            call.respond(HttpStatusCode.NotFound, "Resource not found")
        }
    }
    
    // API endpoints
    route("/api") {
        // Get dashboard information
        get("/dashboard") {
            try {
                val sessions = SessionManager.getAllSessions()
                var totalAgents = 0
                var totalThreads = 0
                var totalMessages = 0
                
                val sessionInfos = sessions.map { session ->
                    val threads = session.getAllThreads()
                    val agentCount = session.getRegisteredAgentsCount()
                    val threadCount = threads.size
                    
                    totalAgents += agentCount
                    totalThreads += threadCount
                    threads.forEach { thread ->
                        totalMessages += thread.messages.size
                    }
                    
                    SessionInfo(
                        id = session.id,
                        applicationId = session.applicationId,
                        agentCount = agentCount,
                        threadCount = threadCount
                    )
                }
                
                val dashboardInfo = DashboardInfo(
                    sessions = sessionInfos,
                    totalAgents = totalAgents,
                    totalThreads = totalThreads,
                    totalMessages = totalMessages
                )
                
                call.respondText(Json.encodeToString(dashboardInfo), ContentType.Application.Json)
            } catch (e: Exception) {
                logger.error(e) { "Error getting dashboard information" }
                call.respond(HttpStatusCode.InternalServerError, "Error getting dashboard information: ${e.message}")
            }
        }
        
        // Get all sessions
        get("/sessions") {
            try {
                val sessions = SessionManager.getAllSessions()
                val sessionInfos = sessions.map { session ->
                    SessionInfo(
                        id = session.id,
                        applicationId = session.applicationId,
                        agentCount = session.getRegisteredAgentsCount(),
                        threadCount = session.getAllThreads().size
                    )
                }
                
                call.respondText(Json.encodeToString(sessionInfos), ContentType.Application.Json)
            } catch (e: Exception) {
                logger.error(e) { "Error getting sessions" }
                call.respond(HttpStatusCode.InternalServerError, "Error getting sessions: ${e.message}")
            }
        }
        
        // Get session details
        get("/sessions/{sessionId}") {
            try {
                val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sessionId")
                val session = SessionManager.getSession(sessionId) ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
                
                val sessionInfo = SessionInfo(
                    id = session.id,
                    applicationId = session.applicationId,
                    agentCount = session.getRegisteredAgentsCount(),
                    threadCount = session.getAllThreads().size
                )
                
                call.respondText(Json.encodeToString(sessionInfo), ContentType.Application.Json)
            } catch (e: Exception) {
                logger.error(e) { "Error getting session details" }
                call.respond(HttpStatusCode.InternalServerError, "Error getting session details: ${e.message}")
            }
        }
        
        // Get all agents in a session
        get("/sessions/{sessionId}/agents") {
            try {
                val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sessionId")
                val session = SessionManager.getSession(sessionId) ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
                
                val agents = session.getAllAgents()
                val agentInfos = agents.map { agent ->
                    AgentInfo(
                        id = agent.id,
                        name = agent.name,
                        description = agent.description
                    )
                }
                
                call.respondText(Json.encodeToString(agentInfos), ContentType.Application.Json)
            } catch (e: Exception) {
                logger.error(e) { "Error getting agents" }
                call.respond(HttpStatusCode.InternalServerError, "Error getting agents: ${e.message}")
            }
        }
        
        // Get all threads in a session
        get("/sessions/{sessionId}/threads") {
            try {
                val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sessionId")
                val session = SessionManager.getSession(sessionId) ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
                
                val threads = session.getAllThreads()
                val threadInfos = threads.map { thread ->
                    ThreadInfo(
                        id = thread.id,
                        name = thread.name,
                        creatorId = thread.creatorId,
                        participants = thread.participants,
                        messageCount = thread.messages.size,
                        isClosed = thread.isClosed,
                        summary = thread.summary
                    )
                }
                
                call.respondText(Json.encodeToString(threadInfos), ContentType.Application.Json)
            } catch (e: Exception) {
                logger.error(e) { "Error getting threads" }
                call.respond(HttpStatusCode.InternalServerError, "Error getting threads: ${e.message}")
            }
        }
        
        // Get thread details
        get("/sessions/{sessionId}/threads/{threadId}") {
            try {
                val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sessionId")
                val threadId = call.parameters["threadId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing threadId")
                
                val session = SessionManager.getSession(sessionId) ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
                val thread = session.getThread(threadId) ?: return@get call.respond(HttpStatusCode.NotFound, "Thread not found")
                
                val threadInfo = ThreadInfo(
                    id = thread.id,
                    name = thread.name,
                    creatorId = thread.creatorId,
                    participants = thread.participants,
                    messageCount = thread.messages.size,
                    isClosed = thread.isClosed,
                    summary = thread.summary
                )
                
                call.respondText(Json.encodeToString(threadInfo), ContentType.Application.Json)
            } catch (e: Exception) {
                logger.error(e) { "Error getting thread details" }
                call.respond(HttpStatusCode.InternalServerError, "Error getting thread details: ${e.message}")
            }
        }
        
        // Get messages in a thread
        get("/sessions/{sessionId}/threads/{threadId}/messages") {
            try {
                val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sessionId")
                val threadId = call.parameters["threadId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing threadId")
                
                val session = SessionManager.getSession(sessionId) ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
                val thread = session.getThread(threadId) ?: return@get call.respond(HttpStatusCode.NotFound, "Thread not found")
                
                val messageInfos = thread.messages.map { message ->
                    MessageInfo(
                        id = message.id,
                        threadId = message.threadId,
                        senderId = message.senderId,
                        content = message.content,
                        mentions = message.mentions,
                        timestamp = message.timestamp
                    )
                }
                
                call.respondText(Json.encodeToString(messageInfos), ContentType.Application.Json)
            } catch (e: Exception) {
                logger.error(e) { "Error getting messages" }
                call.respond(HttpStatusCode.InternalServerError, "Error getting messages: ${e.message}")
            }
        }
    }
}
