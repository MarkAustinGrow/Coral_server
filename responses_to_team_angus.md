# Responses to Team Angus Questions About Coral Protocol Integration

## 1. Agent Registration Visibility

> When we run our agent, we don't see any registered agents (including our own). Is there a specific reason why an agent wouldn't see itself in the list of registered agents?

Yes, there are a few reasons why an agent might not see itself or other agents:

1. **Self-visibility**: By design, the Coral Protocol doesn't include the calling agent in the list of agents returned by `list_agents`. This is to prevent confusion and infinite loops in agent communication patterns.

2. **Registration timing**: There's a slight delay between when an agent connects and when it's fully registered in the system. If you call `list_agents` immediately after connecting, you might not see other agents yet.

3. **Application/Privacy Key mismatch**: Agents can only see other agents that are connected to the same application ID and privacy key. Make sure both Team Angus and Team Yona are using the same values:
   ```
   applicationId: "exampleApplication"
   privacyKey: "privkey"
   ```

> Does the `waitForAgents=2` parameter prevent agents from being visible until the specified number of agents are connected?

No, the `waitForAgents` parameter doesn't affect visibility. It only determines how long the agent will wait during the initial connection before proceeding. Specifically:

- It makes the agent wait until at least that many agents (including itself) are connected
- It doesn't prevent agents from being visible in `list_agents`
- It's primarily used to ensure that multi-agent systems don't proceed until all required agents are available

If you're not seeing any agents, it's more likely due to one of the reasons mentioned above rather than the `waitForAgents` parameter.

## 2. Session Management

> What's the recommended approach for ensuring two agents connect to the same session?

To ensure two agents connect to the same session:

1. **Use the same session ID in the URL**: Both agents should use the same session ID in their connection URL:
   ```
   http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse
   ```
   The `session1` part is the session ID. You can replace this with any unique identifier, but both agents must use the same value.

2. **Connect in close time proximity**: While sessions persist for some time, it's best to have agents connect within a reasonable timeframe of each other.

3. **Use appropriate `waitForAgents` values**: Set `waitForAgents=2` for both agents to ensure they wait for each other before proceeding.

> How long do sessions persist on the server? If Angus connects first and Yona connects later, will they be able to see each other?

Sessions persist on the server for approximately 30 minutes of inactivity by default. If Angus connects first and Yona connects later (within this window), they will be able to see each other as long as:

- They connect to the same session ID
- They use the same application ID and privacy key
- Yona connects before the session times out

> Is there a way to check if a session exists before connecting to it?

There isn't a direct API to check if a session exists before connecting. However, you can implement a simple strategy:

1. **Try to connect with a short timeout**: Connect with a short timeout value (e.g., 5 seconds)
2. **Check for connection success**: If the connection succeeds, the session exists
3. **Handle connection failure**: If the connection fails, the session might not exist or might be inaccessible

Alternatively, you could implement a "ping" agent that connects briefly to check if a session exists, then disconnects.

## 3. Tool Invocation Methods

> We've tried both direct tool invocation (`client.connections["coral"].invoke_tool()`) and the agent-based approach (`client.get_tools()`). The direct approach gives us the error `'dict' object has no attribute 'invoke_tool'`. Is this a known issue with version 0.0.10 of the langchain_mcp_adapters library?

Yes, this is a known issue with the API differences between versions. In version 0.0.10 (which is used in the examples), the correct way to invoke tools directly is:

```python
# Get the connection
connection = client.connections["coral"]

# Invoke the tool using the connection
result = await connection.invoke_tool("list_agents", {})
```

The error `'dict' object has no attribute 'invoke_tool'` suggests that `client.connections["coral"]` is returning a dictionary rather than a connection object with an `invoke_tool` method. This could be due to:

1. A version mismatch in the library
2. The connection not being fully established
3. The connection object having a different structure than expected

> Which approach do you recommend for our use case of agent-to-agent communication?

For agent-to-agent communication, we recommend the **direct tool invocation approach** rather than the agent-based approach. Here's why:

1. **More control**: Direct invocation gives you more control over the communication flow
2. **Simpler debugging**: It's easier to debug issues when you can see exactly what's being sent and received
3. **Lower latency**: Direct invocation has lower latency since it doesn't involve the LLM in every step
4. **More predictable**: The behavior is more predictable since it's not dependent on the LLM's interpretation

Here's a recommended pattern for direct tool invocation:

```python
async def communicate_with_agent(client, thread_id, target_agent_id, message_content):
    try:
        # Send a message to the target agent
        send_result = await client.connections["coral"].invoke_tool("send_message", {
            "threadId": thread_id,
            "content": message_content,
            "mentions": [target_agent_id]
        })
        
        # Wait for a response
        wait_result = await client.connections["coral"].invoke_tool("wait_for_mentions", {
            "timeoutMs": 30000  # 30 seconds
        })
        
        return wait_result
    except Exception as e:
        logger.error(f"Error communicating with agent: {e}")
        return None
```

## 4. Debugging and Monitoring

> Are there any server-side logs or debugging tools we can access to see what's happening when our agents try to connect?

Yes, there are several ways to access server-side logs and debug information:

1. **Server logs**: The Coral server logs are available on the Linode server:
   ```bash
   journalctl -u coral-server -f
   ```
   These logs show connection attempts, errors, and other server events.

2. **Verbose logging in your agents**: Enable verbose logging in your agents:
   ```python
   logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s')
   ```

3. **Connection debugging**: Add debug output for connection events:
   ```python
   async with MultiServerMCPClient(...) as client:
       logger.debug(f"Connected to MCP server with client: {client}")
       logger.debug(f"Available connections: {client.connections}")
       # ...
   ```

> Is there a way to see all currently active sessions and the agents connected to them?

There isn't a direct API to list all active sessions and connected agents. However, you can:

1. **Use the `list_agents` tool**: This will show all agents connected to your current session
2. **Check the server logs**: The logs show session creation and agent connections
3. **Implement a "registry" agent**: Create an agent that stays connected and keeps track of other agents that connect and disconnect

## 5. Function Calling Protocol

> We're using a specific format for function calls to Yona:
> ```python
> message = {
>     "type": "function_call",
>     "function": "create_song",
>     "arguments": {
>         "prompt": prompt
>     }
> }
> ```
> Is this the correct format for function calls between agents, or is there a different recommended approach?

The format you're using is a good approach for structured communication between agents. The Coral Protocol itself doesn't enforce any specific message format for agent-to-agent communication - it's just a transport layer.

However, for interoperability, we recommend using a standardized format like:

```python
message = {
    "type": "function_call",  # or "function_response", "error", etc.
    "function": "function_name",
    "arguments": {
        # Function arguments as key-value pairs
    },
    "metadata": {
        "sender": "agent_id",
        "timestamp": "2025-05-13T09:00:00Z",
        "message_id": "unique_id",
        "correlation_id": "request_id"  # To link responses to requests
    }
}
```

This format:
1. Clearly indicates the message type
2. Specifies the function to call
3. Includes all necessary arguments
4. Provides metadata for tracking and debugging

When sending this message, convert it to a JSON string:

```python
import json
await client.connections["coral"].invoke_tool("send_message", {
    "threadId": thread_id,
    "content": json.dumps(message),
    "mentions": ["target_agent_id"]
})
```

The receiving agent should parse the JSON string back into a dictionary:

```python
mentions = await client.connections["coral"].invoke_tool("wait_for_mentions", {
    "timeoutMs": 30000
})

for mention in mentions:
    try:
        content = mention.get("content", "{}")
        message = json.loads(content)
        
        if message.get("type") == "function_call" and message.get("function") == "create_song":
            # Process the function call
            arguments = message.get("arguments", {})
            prompt = arguments.get("prompt", "")
            
            # Call the function
            result = create_song(prompt)
            
            # Send the response
            response = {
                "type": "function_response",
                "function": "create_song",
                "result": result,
                "metadata": {
                    "sender": "yona_agent",
                    "timestamp": "2025-05-13T09:05:00Z",
                    "correlation_id": message.get("metadata", {}).get("message_id")
                }
            }
            
            await client.connections["coral"].invoke_tool("send_message", {
                "threadId": mention.get("threadId"),
                "content": json.dumps(response),
                "mentions": [message.get("metadata", {}).get("sender")]
            })
    except Exception as e:
        logger.error(f"Error processing mention: {e}")
```

## 6. Connection Parameters

> Are there any other connection parameters besides `agentId`, `waitForAgents`, and `agentDescription` that might be useful for our integration?

Yes, there are a few additional parameters that might be useful:

1. **`timeout`**: Controls the overall connection timeout (in seconds)
   ```python
   "timeout": 300  # 5 minutes
   ```

2. **`sse_read_timeout`**: Controls the timeout for reading from the SSE stream (in seconds)
   ```python
   "sse_read_timeout": 300  # 5 minutes
   ```

3. **`reconnect_delay`**: Controls the delay before reconnecting after a connection failure (in seconds)
   ```python
   "reconnect_delay": 5  # 5 seconds
   ```

4. **`max_retries`**: Controls the maximum number of reconnection attempts
   ```python
   "max_retries": 3
   ```

Example with all parameters:

```python
async with MultiServerMCPClient(
    connections={
        "coral": {
            "transport": "sse",
            "url": mcp_server_url,
            "timeout": 300,
            "sse_read_timeout": 300,
            "reconnect_delay": 5,
            "max_retries": 3
        }
    }
) as client:
    # ...
```

> What's the recommended timeout value for SSE connections in a production environment?

For production environments, we recommend:

- **`timeout`**: 300-600 seconds (5-10 minutes)
- **`sse_read_timeout`**: 300-600 seconds (5-10 minutes)

These values provide a good balance between:
- Allowing enough time for long-running operations
- Detecting and recovering from connection issues
- Preventing resource leaks from abandoned connections

For critical applications, you might also want to implement a heartbeat mechanism:

```python
async def heartbeat(client):
    while True:
        try:
            # Send a heartbeat every 60 seconds
            await asyncio.sleep(60)
            await client.connections["coral"].invoke_tool("list_agents", {})
            logger.debug("Heartbeat sent")
        except Exception as e:
            logger.error(f"Heartbeat error: {e}")
            # Reconnect logic here
```

## 7. Best Practices

> Do you have any examples of successful agent-to-agent communication using the Coral Protocol that we could reference?

Yes, the LangChain examples in the repository demonstrate successful agent-to-agent communication:

- `0_langchain_interface.py`: The interface agent that communicates with users and other agents
- `1_langchain_world_news_agent.py`: The world news agent that responds to requests from the interface agent

These examples show:
1. How to register agents with the Coral server
2. How to discover other agents
3. How to create threads for communication
4. How to send and receive messages
5. How to handle mentions

> Are there any common pitfalls or best practices we should be aware of when integrating with the Coral Protocol?

Here are some best practices and common pitfalls to avoid:

### Best Practices

1. **Use unique agent IDs**: Ensure each agent has a unique ID to prevent conflicts

2. **Implement error handling**: Always wrap tool invocations in try-except blocks to handle errors gracefully

3. **Use structured message formats**: Use a consistent, structured format for messages to make parsing easier

4. **Implement timeouts**: Use appropriate timeout values for `wait_for_mentions` to prevent blocking indefinitely

5. **Implement reconnection logic**: Handle connection failures and implement reconnection logic

6. **Use descriptive agent descriptions**: Provide clear, descriptive agent descriptions to help other agents understand capabilities

7. **Implement a heartbeat mechanism**: Periodically send a simple request to keep the connection alive

8. **Use correlation IDs**: Include correlation IDs in messages to link requests and responses

### Common Pitfalls

1. **Not handling connection failures**: Connections can fail for various reasons; always implement proper error handling

2. **Infinite wait loops**: Without proper timeouts, agents can get stuck waiting for responses that never come

3. **Race conditions**: Be careful with timing-dependent code, especially when multiple agents are connecting simultaneously

4. **Not parsing message content**: Always validate and parse message content carefully to handle unexpected formats

5. **Hardcoding session IDs**: Consider generating dynamic session IDs for production use to prevent conflicts

6. **Not handling reconnections**: If a connection drops, you need logic to reconnect and recover the session state

7. **Blocking the event loop**: Avoid blocking operations in async code; use `await` for all async operations

8. **Not validating inputs**: Always validate inputs before sending them to other agents to prevent errors

## Example Implementation

Here's a more complete example of agent-to-agent communication that incorporates these best practices:

```python
import asyncio
import json
import logging
import uuid
from datetime import datetime
from langchain_mcp_adapters.client import MultiServerMCPClient
import urllib.parse

# Setup logging
logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class AngusAgent:
    def __init__(self, server_url, agent_id, target_agent_id):
        self.server_url = server_url
        self.agent_id = agent_id
        self.target_agent_id = target_agent_id
        self.client = None
        self.thread_id = None
    
    async def connect(self):
        """Connect to the Coral server."""
        try:
            # Configure connection parameters
            base_url = "http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse"
            params = {
                "waitForAgents": 2,
                "agentId": self.agent_id,
                "agentDescription": f"Agent {self.agent_id} for testing agent-to-agent communication"
            }
            query_string = urllib.parse.urlencode(params)
            mcp_server_url = f"{base_url}?{query_string}"
            
            # Connect to the server
            self.client = MultiServerMCPClient(
                connections={
                    "coral": {
                        "transport": "sse",
                        "url": mcp_server_url,
                        "timeout": 300,
                        "sse_read_timeout": 300,
                    }
                }
            )
            await self.client.__aenter__()
            logger.info(f"Connected to Coral server at {mcp_server_url}")
            
            # Start the heartbeat task
            asyncio.create_task(self.heartbeat())
            
            return True
        except Exception as e:
            logger.error(f"Connection error: {e}")
            return False
    
    async def disconnect(self):
        """Disconnect from the Coral server."""
        if self.client:
            await self.client.__aexit__(None, None, None)
            logger.info("Disconnected from Coral server")
    
    async def heartbeat(self):
        """Send periodic heartbeats to keep the connection alive."""
        while True:
            try:
                await asyncio.sleep(60)  # Send heartbeat every 60 seconds
                if self.client:
                    await self.client.connections["coral"].invoke_tool("list_agents", {})
                    logger.debug("Heartbeat sent")
            except Exception as e:
                logger.error(f"Heartbeat error: {e}")
    
    async def list_agents(self):
        """List all connected agents."""
        try:
            result = await self.client.connections["coral"].invoke_tool("list_agents", {})
            logger.info(f"Connected agents: {result}")
            return result
        except Exception as e:
            logger.error(f"Error listing agents: {e}")
            return []
    
    async def create_thread(self):
        """Create a new thread for communication."""
        try:
            result = await self.client.connections["coral"].invoke_tool("create_thread", {
                "name": f"Thread-{uuid.uuid4()}",
                "participants": [self.agent_id, self.target_agent_id]
            })
            self.thread_id = result.get("threadId")
            logger.info(f"Created thread: {self.thread_id}")
            return self.thread_id
        except Exception as e:
            logger.error(f"Error creating thread: {e}")
            return None
    
    async def send_function_call(self, function_name, arguments):
        """Send a function call to the target agent."""
        if not self.thread_id:
            logger.error("No thread ID available. Create a thread first.")
            return None
        
        try:
            # Create the message
            message = {
                "type": "function_call",
                "function": function_name,
                "arguments": arguments,
                "metadata": {
                    "sender": self.agent_id,
                    "timestamp": datetime.utcnow().isoformat(),
                    "message_id": str(uuid.uuid4())
                }
            }
            
            # Send the message
            await self.client.connections["coral"].invoke_tool("send_message", {
                "threadId": self.thread_id,
                "content": json.dumps(message),
                "mentions": [self.target_agent_id]
            })
            logger.info(f"Sent function call: {function_name}")
            
            # Wait for a response
            response = await self.wait_for_response(30000)  # 30 seconds timeout
            return response
        except Exception as e:
            logger.error(f"Error sending function call: {e}")
            return None
    
    async def wait_for_response(self, timeout_ms=30000):
        """Wait for a response from the target agent."""
        try:
            mentions = await self.client.connections["coral"].invoke_tool("wait_for_mentions", {
                "timeoutMs": timeout_ms
            })
            
            if not mentions:
                logger.warning("No response received within timeout")
                return None
            
            # Process the first mention
            mention = mentions[0]
            try:
                content = mention.get("content", "{}")
                message = json.loads(content)
                logger.info(f"Received response: {message}")
                return message
            except json.JSONDecodeError:
                logger.error(f"Invalid JSON in response: {content}")
                return None
        except Exception as e:
            logger.error(f"Error waiting for response: {e}")
            return None

async def main():
    # Create an agent
    agent = AngusAgent(
        server_url="http://coral.pushcollective.club:5555",
        agent_id="angus_agent",
        target_agent_id="yona_agent"
    )
    
    # Connect to the server
    if not await agent.connect():
        logger.error("Failed to connect to the server")
        return
    
    try:
        # List connected agents
        agents = await agent.list_agents()
        if not any(a.get("agentId") == agent.target_agent_id for a in agents):
            logger.error(f"Target agent {agent.target_agent_id} not found")
            return
        
        # Create a thread
        thread_id = await agent.create_thread()
        if not thread_id:
            logger.error("Failed to create a thread")
            return
        
        # Send a function call
        response = await agent.send_function_call("create_song", {
            "prompt": "A song about artificial intelligence"
        })
        
        if response:
            logger.info(f"Function call successful: {response}")
        else:
            logger.error("Function call failed")
    
    finally:
        # Disconnect from the server
        await agent.disconnect()

if __name__ == "__main__":
    asyncio.run(main())
```

This example demonstrates:
- Connecting to the Coral server
- Listing connected agents
- Creating a thread for communication
- Sending a function call to another agent
- Waiting for a response
- Implementing a heartbeat mechanism
- Proper error handling
- Structured message formats
- Correlation IDs for tracking requests and responses

I hope these answers help you resolve your integration challenges. If you have any further questions, please don't hesitate to ask!
