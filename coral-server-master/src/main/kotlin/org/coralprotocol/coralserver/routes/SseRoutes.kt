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
        
        // Track client IP and user agent for better debugging
        val clientIp = call.request.origin.remoteHost
        val userAgent = call.request.headers["User-Agent"] ?: "Unknown"
        logger.info { "SSE connection from $clientIp using $userAgent" }
        
        try {
            val result = handleSseConnection(
                call.parameters,
                this,
                servers,
                isDevMode = false
            )
            
            if (result) {
                logger.info { "SSE connection established successfully for session ${call.parameters["coralSessionId"]}" }
                
                // Set up connection close handler
                call.request.pipeline.intercept(ApplicationReceivePipeline.After) {
                    try {
                        // This will be called when the connection is closed
                        logger.info { "SSE connection closed normally for session ${call.parameters["coralSessionId"]}" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Error in SSE connection close handler" }
                    }
                }
            } else {
                logger.warn { "SSE connection failed for session ${call.parameters["coralSessionId"]}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error establishing SSE connection: ${e.message}" }
            // Don't try to respond here as it might cause another exception
        }
    }

    sse("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        logger.info { "DevMode SSE connection attempt: ${call.request.uri}" }
        logger.debug { "DevMode SSE connection headers: ${call.request.headers.entries().joinToString()}" }
        logger.debug { "DevMode SSE connection parameters: ${call.parameters.entries().joinToString()}" }
        
        // Track client IP and user agent for better debugging
        val clientIp = call.request.origin.remoteHost
        val userAgent = call.request.headers["User-Agent"] ?: "Unknown"
        logger.info { "DevMode SSE connection from $clientIp using $userAgent" }
        
        try {
            val result = handleSseConnection(
                call.parameters,
                this,
                servers,
                isDevMode = true
            )
            
            if (result) {
                logger.info { "DevMode SSE connection established successfully for session ${call.parameters["coralSessionId"]}" }
                
                // Set up connection close handler
                call.request.pipeline.intercept(ApplicationReceivePipeline.After) {
                    try {
                        // This will be called when the connection is closed
                        logger.info { "DevMode SSE connection closed normally for session ${call.parameters["coralSessionId"]}" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Error in DevMode SSE connection close handler" }
                    }
                }
            } else {
                logger.warn { "DevMode SSE connection failed for session ${call.parameters["coralSessionId"]}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error establishing DevMode SSE connection: ${e.message}" }
            // Don't try to respond here as it might cause another exception
        }
    }
    
    // Add a health check endpoint
    get("/health") {
        call.respond(HttpStatusCode.OK, "Coral Server is running")
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
    // Add a timeout for the connection setup
    val connectionStartTime = System.currentTimeMillis()
    val connectionTimeout = 30000 // 30 seconds
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
        
        // Send a welcome message to confirm the connection is working
        try {
            sseProducer.send(
                data = "Connected to Coral Server",
                event = "connection_established",
                id = transportSessionId
            )
            logger.debug { "Sent welcome message to agent $agentId" }
        } catch (e: Exception) {
            logger.warn(e) { "Could not send welcome message to agent $agentId" }
            // Continue anyway as this is not critical
        }
        
        individualServer.connect(transport)
        logger.info { "Agent $agentId successfully connected to session $sessionId" }
        
        // Log connection setup time
        val connectionTime = System.currentTimeMillis() - connectionStartTime
        logger.debug { "Connection setup took $connectionTime ms for agent $agentId" }
    } catch (e: Exception) {
        logger.error(e) { "Error connecting transport for agent $agentId: ${e.message}" }
        
        // Check if this is a timeout
        if (System.currentTimeMillis() - connectionStartTime > connectionTimeout) {
            logger.error { "Connection setup timed out for agent $agentId" }
        }
        
        // Clean up the server entry if it was added
        if (transportSessionId in servers) {
            logger.debug { "Removing failed server for transport session ID: $transportSessionId" }
            servers.remove(transportSessionId)
        }
        
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
