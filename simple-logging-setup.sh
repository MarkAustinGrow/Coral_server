#!/bin/bash

# Simple Logging and RegisterAgent Tool Setup Script
# This script implements a simpler approach to add logging and the register_agent tool

# Exit on error
set -e

echo "=== Simple Logging and RegisterAgent Tool Setup ==="
echo "This script will:"
echo "1. Switch to the master branch"
echo "2. Set up debug logging"
echo "3. Add the register_agent tool"
echo "4. Rebuild and restart the Docker container"
echo ""

# 1. Switch to the master branch
echo "=== Switching to master branch ==="
git checkout master
git pull origin master

# 2. Create logs directory and logging configuration
echo "=== Setting up logging configuration ==="
mkdir -p logs

cat > logs/simplelogger.properties << 'EOF'
# Set the default logging level to DEBUG
org.slf4j.simpleLogger.defaultLogLevel=DEBUG

# Log specific packages at different levels
org.slf4j.simpleLogger.log.org.coralprotocol=DEBUG
org.slf4j.simpleLogger.log.io.ktor=INFO

# Show date and time in logs
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSS

# Show thread name
org.slf4j.simpleLogger.showThreadName=true

# Show log name
org.slf4j.simpleLogger.showLogName=true

# Show short log name
org.slf4j.simpleLogger.showShortLogName=true
EOF

# 3. Create a custom Docker Compose file with logging enabled
echo "=== Creating debug Docker Compose file ==="
cat > docker-compose-debug.yml << 'EOF'
version: '3'
services:
  coral-server:
    build: .
    ports:
      - "3001:3001"
    restart: unless-stopped
    environment:
      - JAVA_OPTS=-Dorg.slf4j.simpleLogger.configurationFile=/app/logs/simplelogger.properties
    volumes:
      - ./logs:/app/logs
EOF

# 4. Add the RegisterAgentTool.kt file
echo "=== Adding RegisterAgentTool.kt ==="
mkdir -p coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/tools/

cat > coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/tools/RegisterAgentTool.kt << 'EOF'
package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

/**
 * Input for the register_agent tool.
 */
@Serializable
data class RegisterAgentInput(
    val name: String,
    val description: String = ""
)

/**
 * Extension function to add the register agent tool to a server.
 */
fun CoralAgentIndividualMcp.addRegisterAgentTool() {
    addTool(
        name = "register_agent",
        description = "Register an agent in the system.",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "name" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The name of the agent")
                        )
                    ),
                    "description" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("A description of the agent")
                        )
                    )
                )
            ),
            required = listOf("name")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<RegisterAgentInput>(request.arguments.toString())
            
            // Create a new agent with the provided details
            val agent = Agent(
                id = connectedAgentId,
                name = input.name,
                description = input.description
            )
            
            // Register the agent in the session
            val success = coralAgentGraphSession.registerAgent(agent)
            
            if (success) {
                CallToolResult(
                    content = listOf(
                        TextContent(
                            """
                            Agent registered successfully:
                            ID: ${agent.id}
                            Name: ${agent.name}
                            Description: ${agent.description}
                            """.trimIndent()
                        )
                    )
                )
            } else {
                CallToolResult(
                    content = listOf(
                        TextContent("Agent with ID ${agent.id} already exists")
                    )
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error registering agent: ${e.message}"
            
            CallToolResult(
                content = listOf(
                    TextContent(errorMessage)
                )
            )
        }
    }
}
EOF

# 5. Update the ThreadToolsRegistry.kt file
echo "=== Updating ThreadToolsRegistry.kt ==="
cat > coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/tools/ThreadToolsRegistry.kt << 'EOF'
package org.coralprotocol.coralserver.tools

import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

/**
 * Extension function to add all thread-based tools to a server.
 */
fun CoralAgentIndividualMcp.addThreadTools() {
    // Agent management tools
    addRegisterAgentTool()
    addListAgentsTool()
    
    // Thread management tools
    addCreateThreadTool()
    addAddParticipantTool()
    addRemoveParticipantTool()
    addCloseThreadTool()
    
    // Messaging tools
    addSendMessageTool()
    addWaitForMentionsTool()
}
EOF

# 6. Commit the changes
echo "=== Committing changes ==="
git add coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/tools/RegisterAgentTool.kt
git add coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/tools/ThreadToolsRegistry.kt
git commit -m "Add RegisterAgentTool and update ThreadToolsRegistry"

# 7. Stop the current container and start with debug logging
echo "=== Stopping current container ==="
docker compose down

echo "=== Building and starting with debug configuration ==="
docker compose -f docker-compose-debug.yml up -d --build

# 8. Wait for the container to start
echo "=== Waiting for the container to start ==="
sleep 10

# 9. Check if the container is running
echo "=== Checking if the container is running ==="
docker ps | grep coral-server

# 10. Display the logs
echo "=== Displaying the logs ==="
docker logs coral_server-coral-server-1

echo ""
echo "=== Setup Complete ==="
echo "The Coral server should now be running with debug logging and the register_agent tool."
echo ""
echo "To view the logs, run: docker logs coral_server-coral-server-1"
echo "To stop the server, run: docker compose -f docker-compose-debug.yml down"
