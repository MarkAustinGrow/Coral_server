# Coral Server Fixes for Timeout and Protocol Issues

## Issues Identified

### 1. Timeout Configuration Issue ✅ FIXED
- **Problem**: The `WaitForMentionsTool` was marking `timeoutMs` as a required parameter
- **Expected**: Should be optional with 30-second default (as per GitHub example)
- **Fix Applied**: Changed `required = listOf("timeoutMs")` to `required = emptyList()`
- **Result**: Agents can now call `wait_for_mentions` without specifying timeout, using the 30-second default

### 2. Client-Side Timeout Issue ⚠️ NEEDS CLIENT FIX
- **Problem**: Your logs show 8000ms timeout, but server default is 30000ms
- **Root Cause**: The 8000ms timeout is coming from your agent client code, not the server
- **Solution**: Update your agent code to either:
  - Remove the `timeoutMs` parameter to use the 30-second default
  - Increase the `timeoutMs` value to 30000 or higher

### 3. MCP Protocol Compatibility Issues ⚠️ NEEDS INVESTIGATION
- **Problem**: "Key method is missing in the map" errors for `notifications/initialized`
- **Root Cause**: Version mismatch between MCP Kotlin SDK (0.4.0) and LangChain MCP adapters
- **Current Server**: Uses `io.modelcontextprotocol:kotlin-sdk:0.4.0`
- **Client Recommendation**: Use `langchain_mcp_adapters==0.0.10` (as per documentation)

## Configuration Verification ✅ CORRECT

### Server Configuration
- **Port**: 5555 ✅
- **Application ID**: "exampleApplication" ✅
- **Privacy Key**: "privkey" ✅
- **Default Timeout**: 30000ms (30 seconds) ✅

### Expected Client Connection
```
http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse
```

### Expected Client Parameters
```python
params = {
    "waitForAgents": 2,  # For two-agent setup
    "agentId": "your_agent_id",
    "agentDescription": "Your agent description"
}
```

### Expected Client Timeouts
```python
connections = {
    "coral": {
        "transport": "sse",
        "url": mcp_server_url,
        "timeout": 300,        # 5 minutes
        "sse_read_timeout": 300,  # 5 minutes
    }
}
```

## Recommended Client-Side Fixes

### 1. Fix Agent Timeout Configuration
Update your agent code to use longer timeouts:

```python
# Option 1: Use default timeout (recommended)
tools = client.get_tools()
agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)
result = await agent_executor.ainvoke({})  # Uses 30-second default

# Option 2: Specify longer timeout explicitly
# If you need to call wait_for_mentions directly:
await client.connections["coral"].invoke_tool("wait_for_mentions", {"timeoutMs": 30000})
```

### 2. Verify LangChain MCP Adapters Version
```bash
pip install langchain_mcp_adapters==0.0.10
```

### 3. Use Agent-Based Approach (Recommended)
Follow the GitHub example pattern:
```python
# Get tools from client
tools = client.get_tools()

# Create agent with tools
agent = create_tool_calling_agent(model, tools, prompt)
agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)

# Let agent handle tool calls automatically
result = await agent_executor.ainvoke({})
```

## Testing Recommendations

### 1. Test with Minimal Example
Use the exact code from the GitHub repository:
- `0_langchain_interface.py`
- `1_langchain_world_news_agent.py`

### 2. Verify Connection Parameters
Ensure your agents use:
- `waitForAgents=2` (for two agents)
- Proper agent IDs and descriptions
- Correct application ID and privacy key

### 3. Monitor Server Logs
Look for:
- Successful agent registration
- Proper timeout values (should show 30000ms, not 8000ms)
- Reduced protocol errors

## Server Changes Made

### File: `src/main/kotlin/org/coralprotocol/coralserver/tools/WaitForMentionsTool.kt`
```kotlin
// BEFORE:
required = listOf("timeoutMs")

// AFTER:
required = emptyList()
```

This change allows agents to call `wait_for_mentions` without specifying a timeout, using the 30-second default from `WaitForMentionsInput`.

## Next Steps

1. **Deploy Updated Server**: Rebuild and deploy the server with the timeout fix
2. **Update Client Code**: Modify your agent code to use proper timeouts and connection parameters
3. **Test with Example Agents**: Verify functionality using the GitHub repository examples
4. **Monitor Logs**: Check for reduced errors and proper timeout values

## Expected Results

After implementing these fixes:
- ✅ Agents should use 30-second timeouts by default
- ✅ Reduced "timeout too short" issues
- ⚠️ MCP protocol errors may persist until client-side version alignment
- ✅ Better compatibility with the GitHub example agents

## Additional Notes

- The server configuration already matches the GitHub example requirements
- The core timeout logic in `CoralAgentGraphSession.kt` is working correctly
- The main issues are in the tool parameter requirements and client-side configuration
