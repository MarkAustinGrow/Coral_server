# Coral Protocol Integration Summary

## Current Status

The Coral server has been successfully modified and deployed to support LangChain agents and enable communication between Team Angus and Team Yona. Here's a summary of what has been accomplished:

### Server Configuration

- **Port**: Changed from 3001 to 5555
- **Application ID**: Added support for `exampleApplication`
- **Privacy Key**: Added support for `privkey`
- **Deployment**: Changes deployed to the Linode server at `coral.pushcollective.club`
- **Firewall**: Port 5555 opened for external connections

### Connection URL

Both teams should use the following connection URL:

```
http://coral.pushcollective.club:5555/devmode/exampleApplication/privkey/session1/sse
```

### Testing

- Created test scripts to verify the connection to the Coral server
- Successfully tested the connection from a local machine
- Verified that the server is properly configured for LangChain agents

## Integration Guidance

### For Team Angus (Caller)

Team Angus is responsible for:

1. Connecting to the Coral server
2. Discovering the Yona agent
3. Creating a thread for communication
4. Sending function calls to the Yona agent
5. Processing responses from the Yona agent

Key recommendations:

- Use the direct tool invocation approach for more control and lower latency
- Implement proper error handling and reconnection logic
- Use structured message formats with metadata for tracking
- Implement a heartbeat mechanism to keep the connection alive

### For Team Yona (Receiver)

Team Yona is responsible for:

1. Connecting to the same Coral server session as Team Angus
2. Listening for function calls from the Angus agent
3. Processing these function calls (e.g., creating songs based on prompts)
4. Sending responses back to the Angus agent

Key recommendations:

- Validate inputs to functions and handle errors gracefully
- Return structured results with metadata
- Implement proper error handling and reconnection logic
- Test functions independently before integration

## Communication Protocol

### Function Call Format

Team Angus should send function calls in this format:

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

### Function Response Format

Team Yona should send responses in this format:

```json
{
    "type": "function_response",
    "function": "create_song",
    "result": {
        "title": "Song about AI",
        "lyrics": "This is a song about AI...",
        "melody": "C G Am F C G F C",
        "created_at": "2025-05-13T09:05:00Z"
    },
    "metadata": {
        "sender": "yona_agent",
        "timestamp": "2025-05-13T09:05:00Z",
        "correlation_id": "unique_id"
    }
}
```

### Error Response Format

Error responses should follow this format:

```json
{
    "type": "error",
    "function": "create_song",
    "error": "Error message here",
    "metadata": {
        "sender": "yona_agent",
        "timestamp": "2025-05-13T09:05:00Z",
        "correlation_id": "unique_id"
    }
}
```

## Common Issues and Solutions

### Connection Issues

- **Issue**: Unable to connect to the Coral server
- **Solution**: Check URL, parameters, and firewall settings

### Agent Discovery Issues

- **Issue**: Unable to see other agents
- **Solution**: Check application ID, privacy key, and session ID

### Tool Invocation Issues

- **Issue**: `'dict' object has no attribute 'invoke_tool'`
- **Solution**: Use the correct API for your version of the library or downgrade to version 0.0.10

### Message Handling Issues

- **Issue**: Not receiving messages from other agents
- **Solution**: Check agent IDs, thread IDs, and mentions

## Example Implementations

Complete example implementations have been provided for both teams:

- **Team Angus**: `responses_to_team_angus.md` contains a complete example of how to connect to the server, discover agents, create threads, and send function calls
- **Team Yona**: `responses_to_team_yona.md` contains a complete example of how to connect to the server, listen for mentions, process function calls, and send responses

## Documentation

The following documentation has been created:

- **`codebase_documentation.md`**: Comprehensive documentation of the Coral server codebase
- **`README-langchain-compatibility.md`**: Overview of the changes made to support LangChain agents
- **`update-coral-server.md`**: Detailed instructions for updating the Linode server
- **`responses_to_team_angus.md`**: Detailed responses to Team Angus's questions
- **`responses_to_team_yona.md`**: Guidance for Team Yona on integrating with the Coral Protocol

## Next Steps

1. **Team Coordination**: Ensure both teams are using the same session ID and agent IDs
2. **Testing**: Test the integration between Team Angus and Team Yona
3. **Monitoring**: Monitor the server logs for any issues
4. **Feedback**: Gather feedback from both teams and make adjustments as needed

## Conclusion

The Coral server is now fully configured to support communication between Team Angus and Team Yona. By following the guidance provided in the documentation, both teams should be able to successfully integrate with the Coral Protocol and communicate with each other.
