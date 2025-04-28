# Coral Server Connection Stability Improvements

This document outlines the changes made to improve the stability of the Coral server, particularly in handling client disconnections and "Broken pipe" errors.

## Problem Overview

The Coral server was experiencing "Broken pipe" errors when clients disconnected unexpectedly. These errors were not being handled gracefully, leading to cascading failures and error messages in the server logs.

The root causes were:

1. Missing error handling for client disconnections
2. No connection health checks before sending messages
3. No cleanup of broken connections
4. Attempts to send responses over already closed connections

## Changes Made

### 1. Enhanced Error Handling in SseServer.kt

- Added specific handling for "Broken pipe" and "Connection reset" errors
- Implemented a utility function to find the root cause of nested exceptions
- Added try-catch blocks around response sending to prevent cascading errors
- Configured SSE with longer ping intervals to keep connections alive

```kotlin
// Handle broken pipe and connection reset errors gracefully
exception<java.io.IOException> { call, cause ->
    when {
        cause.message?.contains("Broken pipe") == true -> {
            logger.warn { "Client disconnected (Broken pipe): ${call.request.uri}" }
            // Don't try to respond as the connection is already closed
        }
        cause.message?.contains("Connection reset") == true -> {
            logger.warn { "Client connection reset: ${call.request.uri}" }
            // Don't try to respond as the connection is already closed
        }
        else -> {
            logger.error(cause) { "I/O exception: ${cause.message}" }
            try {
                call.respond(HttpStatusCode.InternalServerError, "I/O error: ${cause.message}")
            } catch (e: Exception) {
                logger.warn { "Could not send error response: ${e.message}" }
            }
        }
    }
}
```

### 2. Improved Connection Management in SseRoutes.kt

- Added connection tracking with client IP and user agent information
- Implemented connection close handlers to detect when clients disconnect
- Added a welcome message to confirm connections are working
- Added connection timeouts to prevent hanging connections
- Implemented cleanup of failed server entries

```kotlin
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
```

### 3. Enhanced Message Handling in MessageRoutes.kt

- Added transport activity checks before processing messages
- Implemented specific handling for "Broken pipe" errors during message processing
- Added cleanup of broken transports from the servers map
- Added health check endpoints for monitoring

```kotlin
// Check if the transport is still active
if (!isTransportActive(transport)) {
    logger.warn { "Transport for session ID $transportSessionId is no longer active" }
    // Remove the inactive transport from the servers map
    servers.remove(transportSessionId)
    call.respond(HttpStatusCode.Gone, "Transport no longer active")
    return@post
}
```

## Deployment Instructions

To deploy these changes:

1. **Update the source code**:
   - Replace the existing files with the updated versions
   - Ensure all three files are updated: SseServer.kt, SseRoutes.kt, and MessageRoutes.kt

2. **Rebuild the server**:
   ```bash
   cd ~/Coral_server/coral-server-master
   ./gradlew build
   ```

3. **Restart the server**:
   ```bash
   cd ~/Coral_server
   docker compose down
   docker compose up -d
   ```

4. **Monitor the logs**:
   ```bash
   docker logs -f coral_server-coral-server-1
   ```

## Testing the Changes

To test that the changes are working correctly:

1. **Connect to the server**:
   ```bash
   python3 test_coral_client.py --server localhost:3001 --http --devmode
   ```

2. **Check the logs for improved error messages**:
   - You should see more informative log messages
   - "Broken pipe" errors should be handled gracefully with warning messages instead of stack traces

3. **Test connection resilience**:
   - Try disconnecting clients abruptly
   - The server should log the disconnection but continue running without errors
   - Subsequent connections should work without issues

## Expected Benefits

These changes should result in:

1. **Reduced error logs**: "Broken pipe" errors will be handled gracefully
2. **Improved stability**: The server will continue running even when clients disconnect unexpectedly
3. **Better resource management**: Broken connections will be cleaned up automatically
4. **Enhanced monitoring**: Health check endpoints and improved logging will make it easier to monitor the server

## Future Improvements

While these changes significantly improve the server's stability, there are additional enhancements that could be made:

1. **Connection pooling**: Implement a connection pool to better manage client connections
2. **Circuit breaker pattern**: Add circuit breakers to prevent cascading failures
3. **Metrics collection**: Add metrics to track connection statistics and error rates
4. **Automatic reconnection**: Enhance the client to automatically reconnect when connections are lost
