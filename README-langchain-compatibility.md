# LangChain Compatibility for Coral Server

This document explains the changes made to make the Coral server compatible with LangChain agents and how to test the connection.

## Changes Made

We've made the following changes to the Coral server to make it compatible with LangChain agents:

1. **Updated application.yaml**: Added the LangChain expected application ID and privacy key:
   ```yaml
   applications:
     - id: "exampleApplication"
       name: "Example Application"
       description: "Application for LangChain agents"
       privacyKeys:
         - "privkey"
         - "public"
   ```

2. **Updated Main.kt**: Changed the default port from 3001 to 5555:
   ```kotlin
   val port = args.getOrNull(1)?.toIntOrNull() ?: 5555
   ```

## Testing the Connection

We've provided two test scripts to verify the connection to the Coral server:

### Local Testing

Use `test-langchain-connection.py` to test the connection locally:

```bash
# Install required packages
pip install langchain_mcp_adapters

# Run the test script
python test-langchain-connection.py
```

This script will:
1. Connect to the Coral server at `http://localhost:5555/devmode/exampleApplication/privkey/session1/sse`
2. Register a test agent
3. List all registered agents
4. Create a test thread

### Remote Testing

Use `test-langchain-connection-linode.py` to test the connection to the Linode server:

```bash
# Install required packages
pip install langchain_mcp_adapters

# Run the test script with the Linode hostname
python test-langchain-connection-linode.py --hostname coral.pushcollective.club
```

This script accepts the following command-line arguments:
- `--hostname`: The hostname of the Coral server (default: localhost)
- `--port`: The port of the Coral server (default: 5555)

## Deploying to Linode

To deploy these changes to the Linode server, follow the instructions in `update-coral-server.md`. The key steps are:

1. SSH into the Linode server
2. Update the configuration files
3. Build the project
4. Restart the Coral server service

## Using LangChain Agents

Once the Coral server is updated, you can use LangChain agents with the following connection parameters:

```python
base_url = "http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse"
params = {
    "waitForAgents": 2,  # Number of agents to wait for
    "agentId": "your_agent_id",  # Your agent's ID
    "agentDescription": "Your agent's description"  # Your agent's description
}
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
    # Use the client to interact with the server
    tools = client.get_tools()
    # ...
```

## Troubleshooting

If you encounter issues with the connection:

1. **Check the server logs**:
   ```bash
   journalctl -u coral-server -f
   ```

2. **Verify the server is running on the correct port**:
   ```bash
   systemctl status coral-server
   ```

3. **Check firewall settings**:
   ```bash
   iptables -L -n | grep 5555
   ```

4. **Test the connection with curl**:
   ```bash
   curl -v http://localhost:5555/devmode/exampleApplication/privkey/session1/sse
   ```

5. **Increase logging verbosity**:
   Edit the Main.kt file to enable more verbose logging:
   ```kotlin
   System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
   ```

## References

- [LangChain MCP Adapters Documentation](https://python.langchain.com/docs/integrations/providers/mcp)
- [Coral Server Documentation](https://github.com/Coral-Protocol/coral-server)
- [LangChain Example in Coral Server](https://github.com/Coral-Protocol/coral-server/tree/master/examples/langchain)
