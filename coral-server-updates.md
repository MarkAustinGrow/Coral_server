# Coral Server Updates

This document summarizes the updates made to the Coral server documentation and test clients.

## Key Findings

During our investigation, we discovered that the Coral server requires a specific connection flow that wasn't properly documented:

1. **Establish an SSE connection** with the `agentId` parameter
2. **Extract the transport session ID** from the SSE connection
3. **Send tool calls** to the `/message` endpoint with the transport session ID
4. **Use JSON-RPC format** for tool calls

The main issues were:

1. Tool calls were being sent to the base URL instead of the `/message` endpoint
2. The transport session ID wasn't being extracted from the SSE connection
3. Tool calls weren't using the JSON-RPC format

## Updated Files

We've updated the following files to address these issues:

1. **test_coral_client.py**: Updated to use the correct connection flow and JSON-RPC format
2. **coral_client.md**: Updated documentation to explain the correct connection flow
3. **troubleshooting-guide.md**: New guide with solutions for common issues
4. **simple-coral-test.py**: New simple test script that demonstrates the correct connection flow

## How to Test the Coral Server

### Using the Updated Test Client

```bash
# From your local machine (external access)
python test_coral_client.py --server coral.pushcollective.club

# From the Linode server itself (local access)
python test_coral_client.py --server localhost:3001 --http

# Using DevMode (more forgiving for testing)
python test_coral_client.py --server localhost:3001 --http --devmode
```

### Using the Simple Test Script

```bash
# From the Linode server itself (local access)
python simple-coral-test.py --server localhost:3001 --http --devmode

# From your local machine (external access)
python simple-coral-test.py --server coral.pushcollective.club
```

## Connection Flow Explained

### 1. Establish an SSE Connection

```
https://coral.pushcollective.club/default-app/public/session1/sse?agentId=my-agent
```

### 2. Extract the Transport Session ID

The server sends back an SSE event with the event type "endpoint" containing the transport session ID:

```
data: /default-app/public/session1/message?sessionId=TRANSPORT_SESSION_ID
event: endpoint
```

### 3. Send Tool Calls to the Message Endpoint

```
https://coral.pushcollective.club/default-app/public/session1/message?sessionId=TRANSPORT_SESSION_ID
```

### 4. Use JSON-RPC Format for Tool Calls

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

## DevMode vs. Production Mode

The Coral server has two modes:

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

## Troubleshooting

If you encounter any issues, refer to the troubleshooting-guide.md file for detailed solutions.

Common issues include:

1. **404 Not Found Errors**: Usually due to incorrect endpoints or missing transport session ID
2. **400 Bad Request Errors**: Usually due to incorrect message format or missing required fields
3. **SSE Connection Issues**: Usually due to missing agent ID or connection timeout
4. **Server-Side Issues**: Check server logs and configuration

## Next Steps

1. Test the updated clients on your Linode server
2. Update any custom clients to use the correct connection flow
3. Consider enabling trace logging for more detailed debugging information
