# Coral Server Troubleshooting Guide

This guide provides solutions for common issues encountered when connecting to the Coral server.

## Connection Flow Issues

The most common issue when connecting to the Coral server is not following the correct connection flow. The server requires a specific sequence of steps:

1. **Establish an SSE connection** with the `agentId` parameter
2. **Extract the transport session ID** from the SSE connection
3. **Send tool calls** to the message endpoint with the transport session ID

### 404 Not Found Errors

If you're getting 404 errors when trying to send tool calls, check the following:

#### 1. Incorrect Endpoint

**Problem**: Sending tool calls to the base URL instead of the `/message` endpoint.

**Solution**: Tool calls must be sent to the `/message` endpoint, not the base URL.

```
# Incorrect
https://coral.pushcollective.club/default-app/public/session1

# Correct
https://coral.pushcollective.club/default-app/public/session1/message?sessionId=TRANSPORT_SESSION_ID
```

#### 2. Missing Transport Session ID

**Problem**: Not including the transport session ID in the message endpoint URL.

**Solution**: Extract the transport session ID from the SSE connection and include it as a query parameter in the message endpoint URL.

```javascript
// Example of extracting the transport session ID from SSE events
eventSource.addEventListener('endpoint', (event) => {
  // The event data contains the full message endpoint URL with the transport session ID
  const messageEndpoint = event.data;
  const match = messageEndpoint.match(/sessionId=([a-zA-Z0-9-]+)/);
  if (match) {
    const transportSessionId = match[1];
    console.log(`Transport Session ID: ${transportSessionId}`);
    // Use this ID in subsequent tool calls
  }
});
```

### 400 Bad Request Errors

If you're getting 400 errors when trying to send tool calls, check the following:

#### 1. Incorrect Message Format

**Problem**: Using the wrong format for tool calls.

**Solution**: Tool calls must be sent in JSON-RPC format:

```json
{
  "jsonrpc": "2.0",
  "id": "REQUEST_ID",
  "method": "tool_call",
  "params": {
    "tool": "TOOL_NAME",
    "args": {
      // Tool-specific arguments
    }
  }
}
```

#### 2. Missing Required Fields

**Problem**: Missing required fields in the JSON-RPC request.

**Solution**: Ensure all required fields are included:
- `jsonrpc`: Must be "2.0"
- `id`: A unique identifier for the request
- `method`: Must be "tool_call"
- `params`: Contains the tool name and arguments

## SSE Connection Issues

### 1. Missing Agent ID

**Problem**: Not including the `agentId` parameter in the SSE connection URL.

**Solution**: Always include the `agentId` parameter in the SSE connection URL:

```
https://coral.pushcollective.club/default-app/public/session1/sse?agentId=my-agent
```

### 2. Connection Timeout

**Problem**: The SSE connection times out or disconnects.

**Solution**: Implement reconnection logic with exponential backoff:

```javascript
function connectSSE() {
  const eventSource = new EventSource(sseUrl);
  
  eventSource.onerror = (error) => {
    console.error('SSE connection error:', error);
    eventSource.close();
    
    // Reconnect with exponential backoff
    setTimeout(connectSSE, Math.min(1000 * Math.pow(2, retryCount++), 30000));
  };
  
  return eventSource;
}
```

## DevMode vs. Production Mode

The Coral server has two modes: DevMode and Production Mode.

### DevMode

- More forgiving with session creation
- Creates sessions on-demand if they don't exist
- Useful for testing and development

To use DevMode, add `/devmode` to the URL path:

```
https://coral.pushcollective.club/devmode/default-app/public/session1/sse?agentId=my-agent
```

### Production Mode

- Requires sessions to be pre-created
- Enforces stricter validation
- Recommended for production use

## Server-Side Issues

### 1. Server Not Running

**Problem**: The server is not running or not accessible.

**Solution**: Check if the server is running:

```bash
docker ps | grep coral-server
```

If it's not running, start it:

```bash
cd ~/Coral_server
docker compose up -d
```

### 2. Server Logs

**Problem**: Need to check server logs for errors.

**Solution**: View the server logs:

```bash
docker logs coral_server-coral-server-1
```

For more detailed logs, enable trace logging:

```bash
# Edit docker-compose.yml to enable trace logging
sed -i 's/TRACE_MODE=false/TRACE_MODE=true/' docker-compose.yml

# Restart the server
docker compose down
docker compose up -d
```

## Testing with Simple Clients

Sometimes it's helpful to test the server with simple clients to isolate issues.

### 1. Testing SSE Connection

```bash
curl -N "http://localhost:3001/devmode/default-app/public/test-session/sse?agentId=test-agent"
```

### 2. Testing Tool Calls

First, establish an SSE connection and extract the transport session ID. Then, use that ID in the tool call:

```bash
curl -X POST "http://localhost:3001/devmode/default-app/public/test-session/message?sessionId=TRANSPORT_SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "request-123",
    "method": "tool_call",
    "params": {
      "tool": "register_agent",
      "args": {
        "name": "TestAgent",
        "description": "A test agent"
      }
    }
  }'
```

## Common Error Messages

### "Transport not found"

**Problem**: The transport session ID is invalid or expired.

**Solution**: Ensure you're using the correct transport session ID from the SSE connection. If the connection was closed and reopened, you'll need to get a new transport session ID.

### "Session not found"

**Problem**: The session ID doesn't exist on the server.

**Solution**: In production mode, ensure the session exists. In DevMode, try using a different session ID or check if the server is configured to create sessions on-demand.

### "Field 'id' is required for type with serial name 'io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse'"

**Problem**: Missing the `id` field in the JSON-RPC request.

**Solution**: Ensure your JSON-RPC request includes the `id` field:

```json
{
  "jsonrpc": "2.0",
  "id": "request-123",  // This field is required
  "method": "tool_call",
  "params": {
    // ...
  }
}
```

## Advanced Debugging

### 1. Network Inspection

Use browser developer tools to inspect network traffic:

1. Open the Network tab in Chrome DevTools
2. Filter for "EventSource" to see SSE connections
3. Look for XHR/Fetch requests to see tool calls

### 2. Packet Capture

For more detailed network analysis, use tcpdump:

```bash
sudo tcpdump -i any -n port 3001 -A
```

### 3. Server Configuration

Check the server configuration:

```bash
cat ~/Coral_server/docker-compose.yml
```

## Getting Help

If you're still having issues, please:

1. Collect the server logs: `docker logs coral_server-coral-server-1 > server-logs.txt`
2. Capture the client-side error messages
3. Document the steps to reproduce the issue
4. Contact the server administrator or open an issue on the GitHub repository
