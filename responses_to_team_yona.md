# Responses to Team Yona About Coral Protocol Integration

## Overview

This document provides guidance for Team Yona on integrating with the Coral Protocol to enable communication with Team Angus's agent. As the receiving agent in this integration, your role is to:

1. Connect to the same Coral server session as Team Angus
2. Listen for function calls from the Angus agent
3. Process these function calls (e.g., creating songs based on prompts)
4. Send responses back to the Angus agent

## Connection Setup

### Connection URL and Parameters

To connect to the same session as Team Angus, use the following URL and parameters:

```python
# Base URL
base_url = "http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse"

# Connection parameters
params = {
    "waitForAgents": 2,  # Wait for both agents to be connected
    "agentId": "yona_agent",  # Your unique agent ID
    "agentDescription": "Yona agent for creating songs and other creative content"  # Description of your capabilities
}

# Encode parameters and create the full URL
query_string = urllib.parse.urlencode(params)
mcp_server_url = f"{base_url}?{query_string}"

# Connect to the server
async with MultiServerMCPClient(
    connections={
        "coral": {
            "transport": "sse",
            "url": mcp_server_url,
            "timeout": 300,
            "sse_read_timeout": 300,
        }
    }
) as client:
    # Your agent code here
```

### Important Connection Parameters

- **`agentId`**: Must be `"yona_agent"` to match what Team Angus is expecting
- **`waitForAgents`**: Set to `2` to ensure both agents are connected before proceeding
- **`agentDescription`**: Should describe your agent's capabilities to help Team Angus understand what functions you support

## Receiving and Processing Function Calls

### Listening for Mentions

To receive function calls from Team Angus, use the `wait_for_mentions` tool:

```python
mentions = await client.connections["coral"].invoke_tool("wait_for_mentions", {
    "timeoutMs": 30000  # 30 seconds timeout
})

for mention in mentions:
    # Process each mention
    process_mention(mention)
```

### Processing Function Calls

Team Angus will send function calls in this format:

```json
{
    "type": "function_call",
    "function": "create_song",
    "arguments": {
        "prompt": "A song about artificial intelligence"
    },
    "metadata": {
        "sender": "angus_agent",
        "timestamp": "2025-05-13T09:00:00Z",
        "message_id": "unique_id"
    }
}
```

Here's how to process these function calls:

```python
async def process_mention(mention):
    try:
        # Extract content and parse JSON
        content = mention.get("content", "{}")
        message = json.loads(content)
        
        # Check if it's a function call
        if message.get("type") == "function_call":
            function_name = message.get("function")
            arguments = message.get("arguments", {})
            
            # Handle different functions
            if function_name == "create_song":
                prompt = arguments.get("prompt", "")
                result = create_song(prompt)  # Your song creation function
                await send_response(mention, function_name, result, message)
            elif function_name == "other_function":
                # Handle other functions
                pass
            else:
                # Unknown function
                error_message = f"Unknown function: {function_name}"
                await send_error(mention, function_name, error_message, message)
    except json.JSONDecodeError:
        # Invalid JSON
        await send_error(mention, "unknown", "Invalid JSON in message", None)
    except Exception as e:
        # Other errors
        await send_error(mention, "unknown", f"Error processing message: {str(e)}", None)
```

### Sending Responses

When you've processed a function call, send a response back to Team Angus:

```python
async def send_response(mention, function_name, result, original_message):
    # Create response message
    response = {
        "type": "function_response",
        "function": function_name,
        "result": result,
        "metadata": {
            "sender": "yona_agent",
            "timestamp": datetime.utcnow().isoformat(),
            "correlation_id": original_message.get("metadata", {}).get("message_id")
        }
    }
    
    # Send the response
    await client.connections["coral"].invoke_tool("send_message", {
        "threadId": mention.get("threadId"),
        "content": json.dumps(response),
        "mentions": [original_message.get("metadata", {}).get("sender")]
    })
```

### Handling Errors

If you encounter an error while processing a function call, send an error response:

```python
async def send_error(mention, function_name, error_message, original_message):
    # Create error message
    error = {
        "type": "error",
        "function": function_name,
        "error": error_message,
        "metadata": {
            "sender": "yona_agent",
            "timestamp": datetime.utcnow().isoformat(),
            "correlation_id": original_message.get("metadata", {}).get("message_id") if original_message else None
        }
    }
    
    # Send the error
    await client.connections["coral"].invoke_tool("send_message", {
        "threadId": mention.get("threadId"),
        "content": json.dumps(error),
        "mentions": [original_message.get("metadata", {}).get("sender") if original_message else "angus_agent"]
    })
```

## Complete Example Implementation

Here's a complete example implementation for Team Yona:

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

class YonaAgent:
    def __init__(self):
        self.agent_id = "yona_agent"
        self.client = None
    
    async def connect(self):
        """Connect to the Coral server."""
        try:
            # Configure connection parameters
            base_url = "http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse"
            params = {
                "waitForAgents": 2,
                "agentId": self.agent_id,
                "agentDescription": "Yona agent for creating songs and other creative content"
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
    
    async def wait_for_mentions(self, timeout_ms=30000):
        """Wait for mentions from other agents."""
        try:
            mentions = await self.client.connections["coral"].invoke_tool("wait_for_mentions", {
                "timeoutMs": timeout_ms
            })
            
            if mentions:
                logger.info(f"Received {len(mentions)} mentions")
                for mention in mentions:
                    await self.process_mention(mention)
            else:
                logger.debug("No mentions received within timeout")
            
            return mentions
        except Exception as e:
            logger.error(f"Error waiting for mentions: {e}")
            return []
    
    async def process_mention(self, mention):
        """Process a mention from another agent."""
        try:
            # Extract content and parse JSON
            content = mention.get("content", "{}")
            message = json.loads(content)
            
            logger.info(f"Processing mention: {message}")
            
            # Check if it's a function call
            if message.get("type") == "function_call":
                function_name = message.get("function")
                arguments = message.get("arguments", {})
                
                # Handle different functions
                if function_name == "create_song":
                    prompt = arguments.get("prompt", "")
                    result = self.create_song(prompt)
                    await self.send_response(mention, function_name, result, message)
                else:
                    # Unknown function
                    error_message = f"Unknown function: {function_name}"
                    await self.send_error(mention, function_name, error_message, message)
            else:
                # Not a function call
                logger.warning(f"Received message is not a function call: {message}")
        except json.JSONDecodeError:
            # Invalid JSON
            logger.error(f"Invalid JSON in mention: {mention.get('content')}")
            await self.send_error(mention, "unknown", "Invalid JSON in message", None)
        except Exception as e:
            # Other errors
            logger.error(f"Error processing mention: {e}")
            await self.send_error(mention, "unknown", f"Error processing message: {str(e)}", None)
    
    async def send_response(self, mention, function_name, result, original_message):
        """Send a response to a function call."""
        try:
            # Create response message
            response = {
                "type": "function_response",
                "function": function_name,
                "result": result,
                "metadata": {
                    "sender": self.agent_id,
                    "timestamp": datetime.utcnow().isoformat(),
                    "correlation_id": original_message.get("metadata", {}).get("message_id")
                }
            }
            
            # Send the response
            await self.client.connections["coral"].invoke_tool("send_message", {
                "threadId": mention.get("threadId"),
                "content": json.dumps(response),
                "mentions": [original_message.get("metadata", {}).get("sender")]
            })
            
            logger.info(f"Sent response for function: {function_name}")
        except Exception as e:
            logger.error(f"Error sending response: {e}")
    
    async def send_error(self, mention, function_name, error_message, original_message):
        """Send an error response."""
        try:
            # Create error message
            error = {
                "type": "error",
                "function": function_name,
                "error": error_message,
                "metadata": {
                    "sender": self.agent_id,
                    "timestamp": datetime.utcnow().isoformat(),
                    "correlation_id": original_message.get("metadata", {}).get("message_id") if original_message else None
                }
            }
            
            # Send the error
            await self.client.connections["coral"].invoke_tool("send_message", {
                "threadId": mention.get("threadId"),
                "content": json.dumps(error),
                "mentions": [original_message.get("metadata", {}).get("sender") if original_message else "angus_agent"]
            })
            
            logger.info(f"Sent error for function: {function_name}")
        except Exception as e:
            logger.error(f"Error sending error response: {e}")
    
    def create_song(self, prompt):
        """Create a song based on a prompt."""
        logger.info(f"Creating song with prompt: {prompt}")
        
        # This is where you would implement your song creation logic
        # For this example, we'll just return a simple song
        
        song = {
            "title": f"Song about {prompt}",
            "lyrics": f"This is a song about {prompt}.\nIt was created by Yona, the creative agent.\nEnjoy!",
            "melody": "C G Am F C G F C",
            "created_at": datetime.utcnow().isoformat()
        }
        
        return song

async def main():
    # Create the Yona agent
    agent = YonaAgent()
    
    # Connect to the server
    if not await agent.connect():
        logger.error("Failed to connect to the server")
        return
    
    try:
        # List connected agents
        agents = await agent.list_agents()
        
        # Main loop: wait for mentions and process them
        while True:
            await agent.wait_for_mentions(30000)  # 30 seconds timeout
            await asyncio.sleep(1)  # Small delay to prevent tight loop
    
    except KeyboardInterrupt:
        logger.info("Interrupted by user")
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
    finally:
        # Disconnect from the server
        await agent.disconnect()

if __name__ == "__main__":
    asyncio.run(main())
```

## Best Practices for Team Yona

### 1. Function Implementation

When implementing functions that Team Angus will call:

- **Validate inputs**: Always validate the inputs to your functions
- **Handle errors gracefully**: Catch and handle errors in your function implementations
- **Return structured results**: Return results in a consistent, structured format
- **Include metadata**: Include metadata in your responses to help with debugging and tracking

### 2. Connection Management

To ensure reliable connections:

- **Implement heartbeats**: Send periodic heartbeats to keep the connection alive
- **Handle reconnections**: Implement logic to reconnect if the connection is lost
- **Use appropriate timeouts**: Use appropriate timeout values for `wait_for_mentions`
- **Log connection events**: Log connection events for debugging

### 3. Message Processing

When processing messages:

- **Validate message format**: Always validate the format of incoming messages
- **Handle unknown functions**: Have a plan for handling unknown function calls
- **Send error responses**: Send error responses when something goes wrong
- **Include correlation IDs**: Include correlation IDs in responses to link them to requests

### 4. Testing

Before integrating with Team Angus:

- **Test your functions**: Test your functions independently to ensure they work correctly
- **Test with a mock Angus agent**: Create a mock Angus agent to test the integration
- **Test error handling**: Test how your agent handles errors and edge cases
- **Test reconnection logic**: Test how your agent handles connection failures

## Common Issues and Solutions

### 1. Connection Issues

**Issue**: Unable to connect to the Coral server

**Solutions**:
- Check that the server is running
- Check that you're using the correct URL and parameters
- Check that the firewall allows connections to the server
- Try connecting with a shorter timeout to see if the connection is being established

### 2. Agent Discovery Issues

**Issue**: Unable to see other agents

**Solutions**:
- Check that both agents are using the same application ID and privacy key
- Check that both agents are connected to the same session
- Wait a few seconds after connecting before listing agents
- Check the server logs for any errors

### 3. Message Handling Issues

**Issue**: Not receiving messages from Team Angus

**Solutions**:
- Check that you're using the correct agent ID (`yona_agent`)
- Check that you're properly waiting for mentions
- Check that Team Angus is sending messages to the correct thread
- Check that Team Angus is mentioning your agent in their messages

### 4. Function Call Issues

**Issue**: Unable to process function calls

**Solutions**:
- Check that you're correctly parsing the JSON in the message content
- Check that you're handling the function call format correctly
- Check that you're sending responses in the correct format
- Check that you're including the correct metadata in your responses

## Conclusion

By following these guidelines, you should be able to successfully integrate with Team Angus using the Coral Protocol. If you encounter any issues not covered in this document, please reach out for further assistance.
