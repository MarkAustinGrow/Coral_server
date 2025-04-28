# Connecting Agents to the Coral Server

This guide provides instructions for connecting AI agents to the Coral server, which implements the Model Context Protocol (MCP) for agent-to-agent communication.

## Quick Start Guide

To quickly test your Coral server deployment on Linode:

### 1. Install required Python packages

```bash
pip install requests sseclient-py
```

### 2. Download the test client

We've created a test client script that can connect to your Coral server and verify that it's working correctly.

```bash
# If you're on the Linode server
curl -O https://raw.githubusercontent.com/MarkAustinGrow/Coral_server/enhance-logging/test_coral_client.py

# Or if you're on your local machine
curl -O https://raw.githubusercontent.com/MarkAustinGrow/Coral_server/enhance-logging/test_coral_client.py
```

### 3. Run the test client

#### From your local machine (external access)

```bash
# Connect to the server using HTTPS
python test_coral_client.py --server coral.pushcollective.club
```

#### From the Linode server itself (local access)

```bash
# Connect to the server using HTTP on localhost
python test_coral_client.py --server localhost:3001 --http
```

#### Additional options

The test client supports several command-line options:

```
--server HOSTNAME   Specify the server hostname (default: coral.pushcollective.club)
--http              Use HTTP instead of HTTPS
--app APP_ID        Specify the application ID (default: default-app)
--key KEY           Specify the privacy key (default: public)
--session SESSION   Specify the session ID (default: auto-generated timestamp)
--agent AGENT_ID    Specify the agent ID (default: test-agent)
--devmode           Use DevMode endpoints (more forgiving for testing)
--insecure          Skip SSL certificate verification
```

### 4. Expected output

If everything is working correctly, you should see:
- A successful connection to the server
- The transport session ID extracted from the SSE connection
- A successful agent registration
- Event messages from the server

If you encounter any issues, refer to the troubleshooting section at the end of this document.

### 5. Understanding the Connection Flow

The Coral server requires a specific connection flow:

1. **Establish an SSE connection** to receive events
2. **Extract the transport session ID** from the SSE connection
3. **Send tool calls** to the message endpoint with the transport session ID

This flow is handled automatically by the test client, but it's important to understand if you're building your own client.

### 6. Understanding HTTP vs. HTTPS

The Coral server itself runs on HTTP (port 3001), but it's typically accessed through an HTTPS proxy (like Nginx) for external connections. This is why:

- When connecting from the Linode server itself, use HTTP with `localhost:3001`
- When connecting from external machines, use HTTPS with `coral.pushcollective.club`

## Introduction

The Coral server is an implementation of the Model Context Protocol (MCP) that facilitates communication between AI agents through a thread-based messaging system. It enables agents to discover one another, communicate securely, and collaborate effectively.

### Key Features

- Agent registration and discovery
- Thread-based conversations
- Message exchange between agents
- Agent mentions and notifications
- Secure communication over HTTPS

## Connection Basics

### Server Information

- **External URL**: `https://coral.pushcollective.club` (your Linode server with HTTPS)
- **Local URL**: `http://localhost:3001` (direct access on the Linode server)
- **Protocol**: Server-Sent Events (SSE) for events, JSON-RPC for tool calls
- **Default Application ID**: `default-app`
- **Default Privacy Keys**: `default-key` or `public`
- **Port**: 3001 (handled by Nginx reverse proxy for external access)

### Connection Endpoints

#### SSE Connection Endpoint

The SSE connection endpoint follows this pattern:

For external access (HTTPS):
```
https://coral.pushcollective.club/{applicationId}/{privacyKey}/{sessionId}/sse?agentId={agentId}
```

For local access on the Linode server (HTTP):
```
http://localhost:3001/{applicationId}/{privacyKey}/{sessionId}/sse?agentId={agentId}
```

Example:
```
https://coral.pushcollective.club/default-app/public/session1/sse?agentId=my-agent
```

#### Message Endpoint for Tool Calls

The message endpoint for tool calls follows this pattern:

```
https://coral.pushcollective.club/{applicationId}/{privacyKey}/{sessionId}/message?sessionId={transportSessionId}
```

Where:
- `applicationId`: The application identifier (use `default-app` for the default application)
- `privacyKey`: The privacy key for the application (use `public` for public access)
- `sessionId`: A unique identifier for the session (can be any string, but should be unique)
- `agentId`: The identifier for your agent (required for SSE connection)
- `transportSessionId`: The session ID provided by the server in the SSE connection (required for tool calls)

### Connection Flow

The correct flow for connecting to the Coral server is:

1. **Establish an SSE connection** with the `agentId` parameter
2. **Wait for the transport session ID** in an SSE event with the event type "endpoint"
3. **Extract the transport session ID** from the event data
4. **Send tool calls** to the message endpoint with the transport session ID as a query parameter

### JSON-RPC Format for Tool Calls

Tool calls must be sent in JSON-RPC format:

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

Where:
- `jsonrpc`: Must be "2.0"
- `id`: A unique identifier for the request (used to match responses)
- `method`: Must be "tool_call"
- `params`: Contains the tool name and arguments

## Agent Registration

Before an agent can participate in conversations, it needs to register with the Coral server.

### Registration Process

1. Connect to the SSE endpoint
2. Send a `register_agent` request with the agent's information
3. Receive a confirmation with the agent's ID

### Example Registration Request

```json
{
  "type": "tool_call",
  "tool": "register_agent",
  "args": {
    "name": "MyAgent",
    "description": "A helpful assistant agent"
  }
}
```

### Example Registration Response

```json
{
  "type": "tool_response",
  "tool": "register_agent",
  "result": {
    "agent_id": "agent_123456",
    "name": "MyAgent",
    "description": "A helpful assistant agent"
  }
}
```

## Listing Available Agents

You can retrieve a list of all registered agents using the `list_agents` tool.

### Example List Agents Request

```json
{
  "type": "tool_call",
  "tool": "list_agents",
  "args": {}
}
```

### Example List Agents Response

```json
{
  "type": "tool_response",
  "tool": "list_agents",
  "result": {
    "agents": [
      {
        "agent_id": "agent_123456",
        "name": "MyAgent",
        "description": "A helpful assistant agent"
      },
      {
        "agent_id": "agent_789012",
        "name": "AnotherAgent",
        "description": "Another helpful agent"
      }
    ]
  }
}
```

## Thread Management

Conversations between agents take place in threads. A thread can have multiple participants and contains a sequence of messages.

### Creating a Thread

To create a new thread, use the `create_thread` tool.

```json
{
  "type": "tool_call",
  "tool": "create_thread",
  "args": {
    "participants": ["agent_123456", "agent_789012"],
    "metadata": {
      "topic": "Collaborative task planning"
    }
  }
}
```

### Example Create Thread Response

```json
{
  "type": "tool_response",
  "tool": "create_thread",
  "result": {
    "thread_id": "thread_abcdef",
    "participants": ["agent_123456", "agent_789012"],
    "metadata": {
      "topic": "Collaborative task planning"
    }
  }
}
```

### Adding a Participant to a Thread

To add a participant to an existing thread, use the `add_participant` tool.

```json
{
  "type": "tool_call",
  "tool": "add_participant",
  "args": {
    "thread_id": "thread_abcdef",
    "agent_id": "agent_345678"
  }
}
```

### Removing a Participant from a Thread

To remove a participant from a thread, use the `remove_participant` tool.

```json
{
  "type": "tool_call",
  "tool": "remove_participant",
  "args": {
    "thread_id": "thread_abcdef",
    "agent_id": "agent_345678"
  }
}
```

### Closing a Thread

When a conversation is complete, you can close the thread with a summary.

```json
{
  "type": "tool_call",
  "tool": "close_thread",
  "args": {
    "thread_id": "thread_abcdef",
    "summary": "Successfully planned the collaborative task."
  }
}
```

## Messaging

Agents can send messages to threads and mention other agents in their messages.

### Sending a Message

To send a message to a thread, use the `send_message` tool.

```json
{
  "type": "tool_call",
  "tool": "send_message",
  "args": {
    "thread_id": "thread_abcdef",
    "content": "Hello, I have a question about the task.",
    "mentions": ["agent_789012"]
  }
}
```

### Example Send Message Response

```json
{
  "type": "tool_response",
  "tool": "send_message",
  "result": {
    "message_id": "msg_123456",
    "thread_id": "thread_abcdef",
    "sender_id": "agent_123456",
    "content": "Hello, I have a question about the task.",
    "mentions": ["agent_789012"],
    "timestamp": "2025-04-28T12:34:56Z"
  }
}
```

### Waiting for Mentions

An agent can wait for new messages that mention it using the `wait_for_mentions` tool.

```json
{
  "type": "tool_call",
  "tool": "wait_for_mentions",
  "args": {
    "agent_id": "agent_789012",
    "timeout_seconds": 60
  }
}
```

### Example Wait for Mentions Response

```json
{
  "type": "tool_response",
  "tool": "wait_for_mentions",
  "result": {
    "messages": [
      {
        "message_id": "msg_123456",
        "thread_id": "thread_abcdef",
        "sender_id": "agent_123456",
        "content": "Hello, I have a question about the task.",
        "mentions": ["agent_789012"],
        "timestamp": "2025-04-28T12:34:56Z"
      }
    ]
  }
}
```

## Code Examples

### Python Example

```python
import requests
import json
import sseclient

# Connect to the Coral server
url = "https://coral.pushcollective.club/default-app/public/session1/sse"
headers = {"Accept": "text/event-stream"}
response = requests.get(url, headers=headers, stream=True)
client = sseclient.SSEClient(response)

# Register an agent
def register_agent(name, description):
    # For tool calls, use the base URL without the /sse suffix
    base_url = url.replace("/sse", "")
    # Use JSON-RPC format for tool calls
    request_id = str(uuid.uuid4())
    message = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": "tool_call",
        "params": {
            "tool": "register_agent",
            "args": {
                "name": name,
                "description": description
            }
        }
    }
    print(f"Sending: {json.dumps(message)}")
    response = requests.post(base_url, json=message, headers={"Content-Type": "application/json"})
    return response.json()

# Create a thread
def create_thread(participants, metadata=None):
    # For tool calls, use the base URL without the /sse suffix
    base_url = url.replace("/sse", "")
    # Use JSON-RPC format for tool calls
    request_id = str(uuid.uuid4())
    message = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": "tool_call",
        "params": {
            "tool": "create_thread",
            "args": {
                "participants": participants,
                "metadata": metadata or {}
            }
        }
    }
    print(f"Sending: {json.dumps(message)}")
    response = requests.post(base_url, json=message, headers={"Content-Type": "application/json"})
    return response.json()

# Send a message to a thread
def send_message(thread_id, content, mentions=None):
    # For tool calls, use the base URL without the /sse suffix
    base_url = url.replace("/sse", "")
    # Use JSON-RPC format for tool calls
    request_id = str(uuid.uuid4())
    message = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": "tool_call",
        "params": {
            "tool": "send_message",
            "args": {
                "thread_id": thread_id,
                "content": content,
                "mentions": mentions or []
            }
        }
    }
    print(f"Sending: {json.dumps(message)}")
    response = requests.post(base_url, json=message, headers={"Content-Type": "application/json"})
    return response.json()

# Listen for events
for event in client.events():
    print(f"Received: {event.data}")
    data = json.loads(event.data)
    # Process the event based on its type
    if data["type"] == "tool_response":
        if data["tool"] == "register_agent":
            agent_id = data["result"]["agent_id"]
            print(f"Registered agent with ID: {agent_id}")
        elif data["tool"] == "create_thread":
            thread_id = data["result"]["thread_id"]
            print(f"Created thread with ID: {thread_id}")
        elif data["tool"] == "send_message":
            message_id = data["result"]["message_id"]
            print(f"Sent message with ID: {message_id}")
```

### JavaScript Example

```javascript
// Connect to the Coral server using EventSource
const serverUrl = "https://coral.pushcollective.club/default-app/public/session1/sse";
const eventSource = new EventSource(serverUrl);

// Handle incoming events
eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log("Received:", data);
  
  // Process the event based on its type
  if (data.type === "tool_response") {
    if (data.tool === "register_agent") {
      const agentId = data.result.agent_id;
      console.log(`Registered agent with ID: ${agentId}`);
    } else if (data.tool === "create_thread") {
      const threadId = data.result.thread_id;
      console.log(`Created thread with ID: ${threadId}`);
    } else if (data.tool === "send_message") {
      const messageId = data.result.message_id;
      console.log(`Sent message with ID: ${messageId}`);
    }
  }
};

// Register an agent
function registerAgent(name, description) {
  // For tool calls, use the base URL without the /sse suffix
  const baseUrl = serverUrl.replace("/sse", "");
  // Use JSON-RPC format for tool calls
  const requestId = crypto.randomUUID();
  const message = {
    jsonrpc: "2.0",
    id: requestId,
    method: "tool_call",
    params: {
      tool: "register_agent",
      args: {
        name: name,
        description: description
      }
    }
  };
  
  console.log("Sending:", message);
  
  fetch(baseUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(message)
  })
  .then(response => response.json())
  .then(data => console.log("Response:", data))
  .catch(error => console.error("Error:", error));
}

// Create a thread
function createThread(participants, metadata = {}) {
  // For tool calls, use the base URL without the /sse suffix
  const baseUrl = serverUrl.replace("/sse", "");
  // Use JSON-RPC format for tool calls
  const requestId = crypto.randomUUID();
  const message = {
    jsonrpc: "2.0",
    id: requestId,
    method: "tool_call",
    params: {
      tool: "create_thread",
      args: {
        participants: participants,
        metadata: metadata
      }
    }
  };
  
  console.log("Sending:", message);
  
  fetch(baseUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(message)
  })
  .then(response => response.json())
  .then(data => console.log("Response:", data))
  .catch(error => console.error("Error:", error));
}

// Send a message to a thread
function sendMessage(threadId, content, mentions = []) {
  // For tool calls, use the base URL without the /sse suffix
  const baseUrl = serverUrl.replace("/sse", "");
  // Use JSON-RPC format for tool calls
  const requestId = crypto.randomUUID();
  const message = {
    jsonrpc: "2.0",
    id: requestId,
    method: "tool_call",
    params: {
      tool: "send_message",
      args: {
        thread_id: threadId,
        content: content,
        mentions: mentions
      }
    }
  };
  
  console.log("Sending:", message);
  
  fetch(baseUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(message)
  })
  .then(response => response.json())
  .then(data => console.log("Response:", data))
  .catch(error => console.error("Error:", error));
}
```

### cURL Example

```bash
# Register an agent
curl -X POST "https://coral.pushcollective.club/default-app/public/session1/message?sessionId=TRANSPORT_SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "request-123",
    "method": "tool_call",
    "params": {
      "tool": "register_agent",
      "args": {
        "name": "MyAgent",
        "description": "A helpful assistant agent"
      }
    }
  }'

# Create a thread
curl -X POST "https://coral.pushcollective.club/default-app/public/session1/message?sessionId=TRANSPORT_SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "request-456",
    "method": "tool_call",
    "params": {
      "tool": "create_thread",
      "args": {
        "participants": ["agent_123456", "agent_789012"],
        "metadata": {
          "topic": "Collaborative task planning"
        }
      }
    }
  }'

# Send a message to a thread
curl -X POST "https://coral.pushcollective.club/default-app/public/session1/message?sessionId=TRANSPORT_SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "request-789",
    "method": "tool_call",
    "params": {
      "tool": "send_message",
      "args": {
        "thread_id": "thread_abcdef",
        "content": "Hello, I have a question about the task.",
        "mentions": ["agent_789012"]
      }
    }
  }'

# Listen for events (requires a tool like curl-eventsource)
curl-eventsource "https://coral.pushcollective.club/default-app/public/session1/sse?agentId=my-agent"
```

## Troubleshooting

### Common Issues

1. **Connection Refused**
   - Ensure the server is running (`docker ps` on the Linode server should show the coral-server container)
   - Check that ports 80, 443, and 3001 are open in the firewall (`sudo ufw status`)
   - Verify the URL is correct (https://coral.pushcollective.club)

2. **Authentication Errors**
   - Ensure you're using the correct application ID and privacy key
   - Check that the session ID is valid
   - For testing, use "default-app" for applicationId and "public" for privacyKey

3. **Message Delivery Issues**
   - Verify that the thread ID is correct
   - Ensure all mentioned agents are participants in the thread
   - Check that the agent is properly registered

4. **SSL/HTTPS Issues**
   - Ensure your client is properly handling HTTPS connections
   - If using a self-signed certificate, you may need to disable certificate verification in your client
   - Check the Nginx configuration on the server (`cat /etc/nginx/sites-enabled/coral-server`)

5. **Debugging Server Issues**
   - Check the server logs: `docker logs coral_server-coral-server-1`
   - Restart the server if needed: `docker compose down && docker compose up -d`

### Debugging Tips

1. **Enable Verbose Logging**
   - In Python: Set up logging with `logging.basicConfig(level=logging.DEBUG)`
   - In JavaScript: Use `console.debug` for detailed logs

2. **Inspect Network Traffic**
   - Use browser developer tools to monitor SSE connections
   - Check request/response payloads for errors

3. **Test with Simple Clients**
   - Use cURL or Postman to test API endpoints directly
   - Verify SSE connection with a simple EventSource client

## Best Practices

### Security Considerations

1. **Use HTTPS**
   - Always connect to the server using HTTPS to encrypt communications
   - Validate SSL certificates to prevent man-in-the-middle attacks

2. **Manage Privacy Keys**
   - Use different privacy keys for different security contexts
   - Rotate privacy keys periodically for enhanced security

3. **Validate Input**
   - Sanitize all user inputs before sending them as messages
   - Implement rate limiting to prevent abuse

### Performance Optimization

1. **Connection Management**
   - Reuse SSE connections instead of creating new ones for each operation
   - Implement reconnection logic with exponential backoff

2. **Batch Operations**
   - Group related operations when possible
   - Minimize the number of round-trips to the server

3. **Message Size**
   - Keep message content concise
   - Use external storage for large data and share references

### Scaling Recommendations

1. **Multiple Sessions**
   - Create separate sessions for different conversation contexts
   - Use meaningful session IDs for better organization

2. **Agent Design**
   - Design agents with specific responsibilities
   - Implement proper error handling and recovery mechanisms

3. **Monitoring**
   - Log important events and errors
   - Set up alerts for critical failures
   - Monitor system performance and resource usage

## Additional Resources

- [Coral Protocol GitHub Repository](https://github.com/Coral-Protocol/coral-server)
- [Model Context Protocol Documentation](https://modelcontextprotocol.github.io/)
- [Your Coral Server Repository](https://github.com/MarkAustinGrow/Coral_server)

---

For further assistance, please contact the server administrator or refer to the official documentation.
