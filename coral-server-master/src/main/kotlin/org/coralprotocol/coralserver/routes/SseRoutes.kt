package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes that handle initial client connections.
 * These endpoints establish bidirectional communication channels and must be hit
 * before any message processing can begin.
 */
fun Routing.sseRoutes(servers: ConcurrentMap<String, Server>) {
    sse("/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        logger.info { "SSE connection attempt: ${call.request.uri}" }
        logger.debug { "SSE connection headers: ${call.request.headers.entries().joinToString()}" }
        logger.debug { "SSE connection parameters: ${call.parameters.entries().joinToString()}" }
        logger.debug { "SSE connection query parameters: ${call.request.queryParameters.entries().joinToString()}" }
        
        val result = handleSseConnection(
            call.parameters,
            this,
            servers,
            isDevMode = false
        )
        
        if (result) {
            logger.info { "SSE connection established successfully for session ${call.parameters["coralSessionId"]}" }
        } else {
            logger.warn { "SSE connection failed for session ${call.parameters["coralSessionId"]}" }
        }
    }

    sse("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        logger.info { "DevMode SSE connection attempt: ${call.request.uri}" }
        logger.debug { "DevMode SSE connection headers: ${call.request.headers.entries().joinToString()}" }
        logger.debug { "DevMode SSE connection parameters: ${call.parameters.entries().joinToString()}" }
        
        val result = handleSseConnection(
            call.parameters,
            this,
            servers,
            isDevMode = true
        )
        
        if (result) {
            logger.info { "DevMode SSE connection established successfully for session ${call.parameters["coralSessionId"]}" }
        } else {
            logger.warn { "DevMode SSE connection failed for session ${call.parameters["coralSessionId"]}" }
        }
    }
}

/**
 * Centralizes SSE connection handling for both production and development modes.
 * Dev mode skips validation and allows on-demand session creation for testing,
 * while production enforces security checks and requires pre-created sessions.
 */
private suspend fun handleSseConnection(
    parameters: Parameters,
    sseProducer: ServerSSESession,
    servers: ConcurrentMap<String, Server>,
    isDevMode: Boolean
): Boolean {
    val applicationId = parameters["applicationId"]
    val privacyKey = parameters["privacyKey"]
    val sessionId = parameters["coralSessionId"]
    val agentId = parameters["agentId"]
    
    logger.debug { "Processing SSE connection: applicationId=$applicationId, privacyKey=$privacyKey, sessionId=$sessionId, agentId=$agentId, isDevMode=$isDevMode" }
    
    if (agentId == null) {
        logger.warn { "Missing agentId parameter in SSE connection request" }
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing agentId parameter")
        return false
    }

    if (applicationId == null || privacyKey == null || sessionId == null) {
        logger.warn { "Missing required parameters in SSE connection request: applicationId=$applicationId, privacyKey=$privacyKey, sessionId=$sessionId" }
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
        return false
    }

    val session = if (isDevMode) {
        logger.debug { "Creating or getting session in DevMode" }
        val waitForAgents = sseProducer.call.request.queryParameters["waitForAgents"]?.toIntOrNull() ?: 0
        val createdSession = SessionManager.getOrCreateSession(sessionId, applicationId, privacyKey)

        if (waitForAgents > 0) {
            createdSession.devRequiredAgentStartCount = waitForAgents
            logger.info { "DevMode: Setting waitForAgents=$waitForAgents for session $sessionId" }
        }

        logger.debug { "DevMode session created/retrieved: $sessionId with ${createdSession.getRegisteredAgentsCount()} agents" }
        createdSession
    } else {
        logger.debug { "Looking up existing session in production mode" }
        val existingSession = SessionManager.getSession(sessionId)
        if (existingSession == null) {
            logger.warn { "Session not found: $sessionId" }
            sseProducer.call.respond(HttpStatusCode.NotFound, "Session not found")
            return false
        }

        if (existingSession.applicationId != applicationId || existingSession.privacyKey != privacyKey) {
            logger.warn { "Invalid application ID or privacy key for session $sessionId" }
            sseProducer.call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
            return false
        }

        logger.debug { "Production session retrieved: $sessionId with ${existingSession.getRegisteredAgentsCount()} agents" }
        existingSession
    }

    val routePrefix = if (isDevMode) "/devmode" else ""
    val messageEndpoint = "$routePrefix/$applicationId/$privacyKey/$sessionId/message"
    logger.debug { "Creating SSE transport with message endpoint: $messageEndpoint" }
    val transport = SseServerTransport(messageEndpoint, sseProducer)

    logger.debug { "Creating individual MCP server for agent $agentId" }
    val individualServer = CoralAgentIndividualMcp(transport, session, agentId)
    session.coralAgentConnections.add(individualServer)

    val transportSessionId = transport.sessionId
    logger.debug { "Adding server with transport session ID: $transportSessionId" }
    servers[transportSessionId] = individualServer
    
    try {
        logger.debug { "Connecting transport for agent $agentId" }
        individualServer.connect(transport)
        logger.info { "Agent $agentId successfully connected to session $sessionId" }
    } catch (e: Exception) {
        logger.error(e) { "Error connecting transport for agent $agentId: ${e.message}" }
        return false
    }

    if (isDevMode) {
        logger.info { "DevMode: Connected to session $sessionId with application $applicationId (waitForAgents=${session.devRequiredAgentStartCount})" }
    }

    // Check if agent is registered
    val agent = session.getAgent(agentId)
    if (agent == null) {
        logger.info { "Agent $agentId is connected but not yet registered in session $sessionId" }
    } else {
        logger.info { "Agent $agentId is already registered in session $sessionId" }
    }

    return true
}
