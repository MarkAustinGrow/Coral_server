# Coral Server Codebase Documentation

## Current State Overview

The Coral server has been modified to support LangChain agents by changing the default port from 3001 to 5555 and adding support for the application ID and privacy key expected by LangChain agents. These changes have been deployed to the Linode server at `coral.pushcollective.club` and are working correctly.

## Changes Made for LangChain Compatibility

### 1. Configuration Changes

#### Updated `application.yaml`

Added a new application configuration for LangChain agents:

```yaml
applications:
  - id: "exampleApplication"
    name: "Example Application"
    description: "Application for LangChain agents"
    privacyKeys:
      - "privkey"
      - "public"
  - id: "default-app"
    name: "Default Application"
    description: "Default application for testing"
    privacyKeys:
      - "default-key"
      - "public"
```

#### Updated `Main.kt`

Changed the default port from 3001 to 5555:

```kotlin
val port = args.getOrNull(1)?.toIntOrNull() ?: 5555
```

### 2. Server Deployment Changes

Updated the systemd service configuration on the Linode server:

```
[Service]
ExecStart=
ExecStart=/usr/bin/java -jar /opt/coral-server/build/libs/coral-server-1.0-SNAPSHOT.jar --sse-server 5555
```

Opened port 5555 in the firewall to allow external connections.

### 3. Testing and Verification

Created test scripts to verify the connection to the Coral server:
- `test-langchain-connection.py`: For local testing
- `test-langchain-connection-linode.py`: For testing the connection to the Linode server

## Codebase Structure

### Server Components

The Coral server is written in Kotlin and has the following main components:

1. **Main Entry Point**: `src/main/kotlin/org/coralprotocol/coralserver/Main.kt`
   - Starts the server on the specified port
   - Loads the configuration

2. **Configuration**: `src/main/kotlin/org/coralprotocol/coralserver/config/`
   - `AppConfig.kt`: Defines the application configuration model
   - `ConfigLoader.kt`: Loads the configuration from `application.yaml`

3. **Server Implementation**: `src/main/kotlin/org/coralprotocol/coralserver/server/`
   - `SseServer.kt`: Implements the SSE (Server-Sent Events) server
   - `CoralAgentIndividualMcp.kt`: Implements the MCP (Model Context Protocol) server for individual agents

4. **Session Management**: `src/main/kotlin/org/coralprotocol/coralserver/session/`
   - `SessionManager.kt`: Manages agent sessions
   - `CoralAgentGraphSession.kt`: Implements the session for agent graphs

5. **Tools**: `src/main/kotlin/org/coralprotocol/coralserver/tools/`
   - `ListAgentsTool.kt`: Lists all registered agents
   - `CreateThreadTool.kt`: Creates a new thread
   - `SendMessageTool.kt`: Sends a message in a thread
   - `WaitForMentionsTool.kt`: Waits for messages that mention the agent
   - And other tools for thread management

6. **Models**: `src/main/kotlin/org/coralprotocol/coralserver/models/`
   - `Agent.kt`: Defines the agent model
   - `Thread.kt`: Defines the thread model
   - `Message.kt`: Defines the message model

7. **Routes**: `src/main/kotlin/org/coralprotocol/coralserver/routes/`
   - `SessionRoutes.kt`: Defines the routes for session management
   - `MessageRoutes.kt`: Defines the routes for message handling
   - `SseRoutes.kt`: Defines the routes for SSE connections

### LangChain Examples

The repository includes LangChain examples in `github-repo/coral-server-master/examples/langchain/`:

1. **Interface Agent**: `0_langchain_interface.py`
   - Implements an agent that interacts with users and coordinates with other agents

2. **World News Agent**: `1_langchain_world_news_agent.py`
   - Implements an agent that fetches news articles based on user queries

3. **Documentation**: `README.md`
   - Explains how to set up and run the LangChain agents

## API Patterns and Usage

### Connection URL

The connection URL for LangChain agents is:

```
http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse
```

### Connection Parameters

When connecting to the Coral server, agents should use these parameters:

```python
params = {
    "waitForAgents": 2,  # Number of agents to wait for
    "agentId": "your_agent_id",  # Your agent's unique ID
    "agentDescription": "Your agent's description"  # A description of your agent's capabilities
}
```

### Tool Access Pattern

The LangChain examples use an agent-based approach for tool access:

1. Get tools from the client:
   ```python
   tools = client.get_tools()
   ```

2. Create an agent with these tools:
   ```python
   agent = create_tool_calling_agent(model, tools, prompt)
   agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)
   ```

3. Let the agent invoke the tools:
   ```python
   await agent_executor.ainvoke({})
   ```

### Available Tools

The Coral server provides these tools:

1. `list_agents`: Lists all connected agents
2. `create_thread`: Creates a new communication thread
3. `add_participant`: Adds a participant to a thread
4. `remove_participant`: Removes a participant from a thread
5. `send_message`: Sends a message in a thread
6. `wait_for_mentions`: Waits for messages that mention the agent
7. `close_thread`: Closes a thread

## Version Compatibility

### LangChain MCP Adapters

The LangChain examples use version 0.0.10 of the `langchain_mcp_adapters` library:

```
langchain-mcp-adapters==0.0.10
```

Team Angus is using version 0.0.11, which may have API differences. The recommended approach is to:

1. Downgrade to version 0.0.10 to match the examples:
   ```bash
   pip install langchain_mcp_adapters==0.0.10
   ```

2. Or adapt the code to use the agent-based approach shown in the examples

### Other Dependencies

The LangChain examples also use:

```
langchain==0.3.25
langchain-core==0.3.58
langchain-openai==0.3.16
```

## Troubleshooting Guide for Team Angus

### Issue: `'dict' object has no attribute 'invoke_tool'`

**Problem**: Team Angus is trying to use `client.connections["coral"].invoke_tool()`, but this pattern doesn't work with their version of the MCP client.

**Solution**: 
1. Use the agent-based approach shown in the examples:
   ```python
   tools = client.get_tools()
   agent = create_tool_calling_agent(model, tools, prompt)
   agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)
   await agent_executor.ainvoke({})
   ```

2. Or downgrade to version 0.0.10 of the `langchain_mcp_adapters` library:
   ```bash
   pip install langchain_mcp_adapters==0.0.10
   ```

### Issue: `'str' object has no attribute 'get'`

**Problem**: The `list_agents` function is returning a string instead of a list of dictionaries.

**Solution**: Parse the string as JSON:
```python
import json
result = await client.connections["coral"].invoke_tool("list_agents", {})
if isinstance(result, str):
    result = json.loads(result)
```

### Issue: 404 Error when Discovering Agents

**Problem**: The agent discovery endpoint is returning a 404 error.

**Solution**:
1. Check that you're using the correct URL format:
   ```
   http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse
   ```

2. Check that you've registered your agent correctly with the required parameters:
   ```python
   params = {
       "waitForAgents": 2,
       "agentId": "your_agent_id",
       "agentDescription": "Your agent's description"
   }
   ```

3. Check that you're using the correct application ID (`exampleApplication`) and privacy key (`privkey`).

### Issue: Session ID Handling

**Problem**: The session ID is not being handled correctly.

**Solution**: The session ID is now handled internally by the LangChain MCP adapter. You don't need to extract or manage it manually.

### Debugging Tips

1. **Enable Verbose Logging**:
   ```python
   logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s')
   ```

2. **Print the Client Object Structure**:
   ```python
   import inspect
   print(dir(client))
   print(inspect.getmembers(client))
   ```

3. **Print the Tools Returned by get_tools()**:
   ```python
   tools = client.get_tools()
   for tool in tools:
       print(f"Tool: {tool.name}, Type: {type(tool)}")
   ```

4. **Enable Verbose Mode in the Agent Executor**:
   ```python
   agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)
   ```

5. **Check the Server Logs**:
   ```bash
   journalctl -u coral-server -f
   ```

## Example Code

### Minimal Working Example

```python
import asyncio
import os
import logging
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.prompts import ChatPromptTemplate
from langchain.chat_models import init_chat_model
from langchain.agents import create_tool_calling_agent, AgentExecutor
import urllib.parse

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

async def main():
    # Configure connection parameters
    base_url = "http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse"
    params = {
        "waitForAgents": 1,
        "agentId": "angus_test_agent",
        "agentDescription": "Test agent for Team Angus"
    }
    query_string = urllib.parse.urlencode(params)
    mcp_server_url = f"{base_url}?{query_string}"
    
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
        logger.info(f"Connected to MCP server at {mcp_server_url}")
        
        # Get tools from the client
        tools = client.get_tools()
        logger.info(f"Retrieved {len(tools)} tools")
        
        # Create a simple prompt that uses the list_agents tool
        prompt = ChatPromptTemplate.from_messages([
            (
                "system",
                """You are a test agent for Team Angus. Your only task is to list all connected agents using the list_agents tool.
                After listing the agents, summarize how many agents you found and their names."""
            ),
            ("placeholder", "{agent_scratchpad}")
        ])
        
        # Initialize the model
        model = init_chat_model(
            model="gpt-4o-mini",
            model_provider="openai",
            api_key=os.getenv("OPENAI_API_KEY"),
            temperature=0.3,
            max_tokens=16000
        )
        
        # Create the agent
        agent = create_tool_calling_agent(model, tools, prompt)
        agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)
        
        # Run the agent
        result = await agent_executor.ainvoke({})
        logger.info(f"Agent result: {result}")

if __name__ == "__main__":
    asyncio.run(main())
```

## Next Steps

1. **Team Angus**: Update their agent code to use the agent-based approach shown in the examples or downgrade to version 0.0.10 of the `langchain_mcp_adapters` library.

2. **Testing**: Continue testing the connection to the Coral server using the provided test scripts.

3. **Documentation**: Keep this documentation updated as the codebase evolves.

4. **Monitoring**: Monitor the server logs for any issues or errors.

5. **Future Enhancements**: Consider adding more tools or improving the existing ones based on user feedback.
