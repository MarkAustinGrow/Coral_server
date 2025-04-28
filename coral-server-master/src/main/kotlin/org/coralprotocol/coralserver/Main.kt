package org.coralprotocol.coralserver

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.runSseMcpServerWithPlainConfiguration

private val logger = KotlinLogging.logger {}

/**
 * Start sse-server mcp on port 3001.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 * - "--debug": Enables debug logging.
 * - "--trace": Enables trace logging (most verbose).
 */
fun main(args: Array<String>) {
    // Process debug flags first
    val debugMode = args.contains("--debug")
    val traceMode = args.contains("--trace")
    
    // Configure logging level
    when {
        traceMode -> {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
            System.setProperty("io.ktor.development", "true")
            logger.info { "Trace logging enabled" }
        }
        debugMode -> {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
            System.setProperty("io.ktor.development", "true")
            logger.info { "Debug logging enabled" }
        }
    }
    
    // Filter out logging flags for command processing
    val filteredArgs = args.filter { it != "--debug" && it != "--trace" }.toTypedArray()
    
    // Process server command
    val command = filteredArgs.firstOrNull() ?: "--sse-server"
    val port = filteredArgs.getOrNull(1)?.toIntOrNull() ?: 3001
    
    logger.info { "Starting Coral server with command: $command, port: $port" }
    
    when (command) {
//        "--stdio" -> runMcpServerUsingStdio()
        "--sse-server" -> runSseMcpServerWithPlainConfiguration(port)
        else -> {
            logger.error { "Unknown command: $command" }
            println("Usage: java -jar coral-server.jar [--debug|--trace] [--sse-server [port]]")
            println("  --debug: Enable debug logging")
            println("  --trace: Enable trace logging (most verbose)")
            println("  --sse-server [port]: Run SSE server on specified port (default: 3001)")
        }
    }
}
