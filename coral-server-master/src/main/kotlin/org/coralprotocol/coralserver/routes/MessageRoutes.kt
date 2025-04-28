package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures message-related routes.
 * 
 * @param servers A concurrent map to store server instances by transport session ID
 */
fun Routing.messageRoutes(servers: ConcurrentMap<String, Server>) {
    // Message endpoint with application, privacy key, and session parameters
    post("/{applicationId}/{privacyKey}/{coralSessionId}/message") {
        logger.info { "Received message for session ${call.parameters["coralSessionId"]}" }
        
        // Log request details
        val requestUri = call.request.uri
        val contentType = call.request.contentType().toString()
        val headers = call.request.headers.entries().joinToString(", ") { "${it.key}: ${it.value.joinToString()}" }
        logger.debug { "Message request: $requestUri | Content-Type: $contentType" }
        logger.debug { "Message headers: $headers" }
        
        // Try to log request body if available
        try {
            val requestBody = call.attributes.getOrNull(AttributeKey("RequestBody"))
            if (requestBody != null) {
                logger.debug { "Message body: $requestBody" }
            }
        } catch (e: Exception) {
            logger.debug { "Could not log message body: ${e.message}" }
        }

        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"]
        val transportSessionId = call.request.queryParameters["sessionId"]
        
        logger.debug { "Message parameters: applicationId=$applicationId, privacyKey=$privacyKey, sessionId=$sessionId, transportSessionId=$transportSessionId" }

        if (applicationId == null || privacyKey == null || sessionId == null || transportSessionId == null) {
            logger.warn { "Missing required parameters in message request: applicationId=$applicationId, privacyKey=$privacyKey, sessionId=$sessionId, transportSessionId=$transportSessionId" }
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
            return@post
        }

        // Get the session
        logger.debug { "Looking up session: $sessionId" }
        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            logger.warn { "Session not found: $sessionId" }
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }
        logger.debug { "Found session with ${session.getRegisteredAgentsCount()} registered agents" }

        // Validate that the application and privacy key match the session
        if (session.applicationId != applicationId || session.privacyKey != privacyKey) {
            logger.warn { "Invalid application ID or privacy key for session $sessionId" }
            call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
            return@post
        }

        // Get the transport
        logger.debug { "Looking up transport for session ID: $transportSessionId" }
        val server = servers[transportSessionId]
        if (server == null) {
            logger.warn { "Server not found for transport session ID: $transportSessionId" }
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }
        
        val transport = server.transport as? SseServerTransport
        if (transport == null) {
            logger.warn { "Transport not found or not an SSE transport for session ID: $transportSessionId" }
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }
        logger.debug { "Found transport: ${transport::class.simpleName}" }

        // Check if the transport is still active
        if (!isTransportActive(transport)) {
            logger.warn { "Transport for session ID $transportSessionId is no longer active" }
            // Remove the inactive transport from the servers map
            servers.remove(transportSessionId)
            call.respond(HttpStatusCode.Gone, "Transport no longer active")
            return@post
        }

        // Handle the message
        try {
            logger.debug { "Processing message with transport: ${transport::class.simpleName}" }
            transport.handlePostMessage(call)
            logger.info { "Message processed successfully for session $sessionId" }
        } catch (e: NoSuchElementException) {
            logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
            call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
        } catch (e: java.io.IOException) {
            // Handle broken pipe and connection reset errors
            if (e.message?.contains("Broken pipe") == true || e.message?.contains("Connection reset") == true) {
                logger.warn { "Client disconnected while processing message: ${e.message}" }
                // Remove the broken transport from the servers map
                servers.remove(transportSessionId)
                // Don't try to respond as the connection is already closed
            } else {
                logger.error(e) { "I/O error processing message: ${e.message}" }
                try {
                    call.respond(HttpStatusCode.InternalServerError, "I/O error: ${e.message}")
                } catch (responseError: Exception) {
                    logger.warn { "Could not send error response: ${responseError.message}" }
                }
            }
        } catch (e: Exception) {
            // Check if this is a nested broken pipe exception
            val rootCause = findRootCause(e)
            if (rootCause is java.io.IOException && 
                (rootCause.message?.contains("Broken pipe") == true || 
                 rootCause.message?.contains("Connection reset") == true)) {
                logger.warn { "Client disconnected (nested exception): ${rootCause.message}" }
                // Remove the broken transport from the servers map
                servers.remove(transportSessionId)
                // Don't try to respond as the connection is already closed
            } else {
                logger.error(e) { "Unexpected error processing message: ${e.message}" }
                try {
                    call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
                } catch (responseError: Exception) {
                    logger.warn { "Could not send error response: ${responseError.message}" }
                }
            }
        }
    }

    // DevMode message endpoint - no validation
    post("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/message") {
        logger.info { "Received DevMode message for session ${call.parameters["coralSessionId"]}" }
        
        // Log request details
        val requestUri = call.request.uri
        val contentType = call.request.contentType().toString()
        val headers = call.request.headers.entries().joinToString(", ") { "${it.key}: ${it.value.joinToString()}" }
        logger.debug { "DevMode message request: $requestUri | Content-Type: $contentType" }
        logger.debug { "DevMode message headers: $headers" }
        
        // Try to log request body if available
        try {
            val requestBody = call.attributes.getOrNull(AttributeKey("RequestBody"))
            if (requestBody != null) {
                logger.debug { "DevMode message body: $requestBody" }
            }
        } catch (e: Exception) {
            logger.debug { "Could not log DevMode message body: ${e.message}" }
        }

        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"]
        val transportSessionId = call.request.queryParameters["sessionId"]
        
        logger.debug { "DevMode message parameters: applicationId=$applicationId, privacyKey=$privacyKey, sessionId=$sessionId, transportSessionId=$transportSessionId" }

        if (applicationId == null || privacyKey == null || sessionId == null || transportSessionId == null) {
            logger.warn { "Missing required parameters in DevMode message request: applicationId=$applicationId, privacyKey=$privacyKey, sessionId=$sessionId, transportSessionId=$transportSessionId" }
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
            return@post
        }

        // Get the session. It should exist even in dev mode as it was created in the sse endpoint
        logger.debug { "Looking up DevMode session: $sessionId" }
        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            logger.warn { "DevMode session not found: $sessionId" }
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }
        logger.debug { "Found DevMode session with ${session.getRegisteredAgentsCount()} registered agents" }

        // Get the transport
        logger.debug { "Looking up DevMode transport for session ID: $transportSessionId" }
        val server = servers[transportSessionId]
        if (server == null) {
            logger.warn { "DevMode server not found for transport session ID: $transportSessionId" }
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }
        
        val transport = server.transport as? SseServerTransport
        if (transport == null) {
            logger.warn { "DevMode transport not found or not an SSE transport for session ID: $transportSessionId" }
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }
        logger.debug { "Found DevMode transport: ${transport::class.simpleName}" }

        // Check if the transport is still active
        if (!isTransportActive(transport)) {
            logger.warn { "DevMode transport for session ID $transportSessionId is no longer active" }
            // Remove the inactive transport from the servers map
            servers.remove(transportSessionId)
            call.respond(HttpStatusCode.Gone, "Transport no longer active")
            return@post
        }

        // Handle the message
        try {
            logger.debug { "Processing DevMode message with transport: ${transport::class.simpleName}" }
            transport.handlePostMessage(call)
            logger.info { "DevMode message processed successfully for session $sessionId" }
        } catch (e: NoSuchElementException) {
            logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
            call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
        } catch (e: java.io.IOException) {
            // Handle broken pipe and connection reset errors
            if (e.message?.contains("Broken pipe") == true || e.message?.contains("Connection reset") == true) {
                logger.warn { "Client disconnected while processing DevMode message: ${e.message}" }
                // Remove the broken transport from the servers map
                servers.remove(transportSessionId)
                // Don't try to respond as the connection is already closed
            } else {
                logger.error(e) { "I/O error processing DevMode message: ${e.message}" }
                try {
                    call.respond(HttpStatusCode.InternalServerError, "I/O error: ${e.message}")
                } catch (responseError: Exception) {
                    logger.warn { "Could not send error response: ${responseError.message}" }
                }
            }
        } catch (e: Exception) {
            // Check if this is a nested broken pipe exception
            val rootCause = findRootCause(e)
            if (rootCause is java.io.IOException && 
                (rootCause.message?.contains("Broken pipe") == true || 
                 rootCause.message?.contains("Connection reset") == true)) {
                logger.warn { "Client disconnected (nested exception) while processing DevMode message: ${rootCause.message}" }
                // Remove the broken transport from the servers map
                servers.remove(transportSessionId)
                // Don't try to respond as the connection is already closed
            } else {
                logger.error(e) { "Unexpected error processing DevMode message: ${e.message}" }
                try {
                    call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
                } catch (responseError: Exception) {
                    logger.warn { "Could not send error response: ${responseError.message}" }
                }
            }
        }
    }
    
    // Add a health check endpoint for the message service
    get("/message-health") {
        call.respond(HttpStatusCode.OK, "Message service is running")
    }
}

/**
 * Utility function to check if a transport is still active
 */
private fun isTransportActive(transport: SseServerTransport): Boolean {
    return try {
        // Try to access a property or method of the transport
        // If this succeeds, the transport is likely still active
        val sessionId = transport.sessionId
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Utility function to find the root cause of an exception
 */
private fun findRootCause(throwable: Throwable): Throwable {
    var rootCause: Throwable = throwable
    while (rootCause.cause != null && rootCause.cause !== rootCause) {
        rootCause = rootCause.cause!!
    }
    return rootCause
}
