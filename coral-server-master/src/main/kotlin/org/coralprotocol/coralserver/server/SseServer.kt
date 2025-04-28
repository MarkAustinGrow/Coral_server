package org.coralprotocol.coralserver.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.routes.messageRoutes
import org.coralprotocol.coralserver.routes.sessionRoutes
import org.coralprotocol.coralserver.routes.sseRoutes
import org.slf4j.event.Level

private val logger = KotlinLogging.logger {}

// Key to store request body for logging
private val requestBodyKey = AttributeKey<String>("RequestBody")

/**
 * Checks HTTPS configuration and logs relevant information
 */
fun checkHttpsConfiguration() {
    logger.info { "Checking HTTPS configuration..." }
    
    // Check if we're behind a proxy
    val behindProxy = System.getenv("BEHIND_PROXY")?.toBoolean() ?: true
    logger.info { "Server is${if (behindProxy) "" else " not"} configured to run behind a proxy" }
    
    // Log expected connection URL
    val baseUrl = if (behindProxy) "https://coral.pushcollective.club" else "http://localhost:3001"
    logger.info { "Expected connection URL: $baseUrl/{applicationId}/{privacyKey}/{sessionId}/sse" }
    
    // Log example connection URL
    logger.info { "Example connection URL: $baseUrl/default-app/public/session1/sse" }
}

/**
 * Runs an SSE MCP server with a plain configuration.
 * 
 * @param port The port to run the server on
 */
fun runSseMcpServerWithPlainConfiguration(port: Int): Unit = runBlocking {
    val mcpServersByTransportId = ConcurrentMap<String, Server>()

    // Load application configuration
    val appConfig = AppConfigLoader.loadConfig()
    logger.info { "Starting sse server on port $port with ${appConfig.applications.size} configured applications" }
    logger.info { "Use inspector to connect to the http://localhost:$port/{applicationId}/{privacyKey}/{sessionId}/sse" }
    
    // Check HTTPS configuration
    checkHttpsConfiguration()

    embeddedServer(CIO, host = "0.0.0.0", port = port, watchPaths = listOf("classes")) {
        // Install CallLogging feature to log HTTP requests
        install(CallLogging) {
            level = Level.INFO
            format { call ->
                val status = call.response.status()
                val httpMethod = call.request.httpMethod.value
                val uri = call.request.uri
                val contentType = call.request.contentType().toString()
                val userAgent = call.request.headers["User-Agent"]
                
                "[$status] $httpMethod $uri | Content-Type: $contentType | User-Agent: $userAgent"
            }
        }
        
        // Install StatusPages for better error handling
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                logger.error(cause) { "Unhandled exception: ${cause.message}" }
                call.respond(HttpStatusCode.InternalServerError, "Internal server error: ${cause.message}")
            }
            
            status(HttpStatusCode.NotFound) { call, status ->
                val requestedPath = call.request.path()
                logger.warn { "404 Not Found: $requestedPath | Method: ${call.request.httpMethod.value}" }
                call.respond(status, "Resource not found: $requestedPath")
            }
        }
        
        // Install CORS with logging
        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Options)
            anyHost()
            allowCredentials = true
            allowNonSimpleContentTypes = true
            
            // Log CORS requests
            onRequest { request ->
                logger.info { "CORS request: ${request.origin} -> ${request.method} ${request.uri}" }
            }
        }
        
        install(SSE)
        
        // Intercept requests to log headers and body
        intercept(ApplicationCallPipeline.Monitoring) {
            val headers = call.request.headers.entries()
                .joinToString("\n  ") { "${it.key}: ${it.value.joinToString()}" }
            
            logger.debug { 
                "Request headers:\n  $headers\n" +
                "Forwarded protocol: ${call.request.headers["X-Forwarded-Proto"]}\n" +
                "Original URI: ${call.request.headers["X-Original-URI"]}"
            }
            
            if (call.request.httpMethod == HttpMethod.Post) {
                try {
                    val requestBody = call.receiveText()
                    logger.debug { "Request body: $requestBody" }
                    call.attributes.put(requestBodyKey, requestBody)
                    
                    // We need to set the request body back for further processing
                    proceedWith(call)
                } catch (e: Exception) {
                    logger.error(e) { "Error reading request body" }
                    proceedWith(call)
                }
            }
        }

        routing {
            // Configure all routes
            sessionRoutes()
            sseRoutes(mcpServersByTransportId)
            messageRoutes(mcpServersByTransportId)
            
            // Log all registered routes
            val routes = application.pluginOrNull(Routing)?.let { 
                it::class.java.getDeclaredField("routes").apply { isAccessible = true }.get(it) as List<*> 
            }
            logger.info { "Registered routes:" }
            routes?.forEach { route ->
                val selector = route::class.java.getDeclaredField("selector").apply { isAccessible = true }.get(route)
                logger.info { " - $selector" }
            }
        }
    }.start(wait = true)
}
