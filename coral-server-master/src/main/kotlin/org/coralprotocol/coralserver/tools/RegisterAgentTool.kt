package org.coralprotocol.coralserver.tools

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

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
            logger.info { "Processing register_agent request for agent: ${connectedAgentId}" }
            
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<RegisterAgentInput>(request.arguments.toString())
            
            logger.debug { "Register agent input: name=${input.name}, description=${input.description}" }
            
            // Create a new agent with the provided details
            val agent = Agent(
                id = connectedAgentId,
                name = input.name,
                description = input.description
            )
            
            // Register the agent in the session
            val success = coralAgentGraphSession.registerAgent(agent)
            
            if (success) {
                logger.info { "Successfully registered agent: ${agent.id} (${agent.name})" }
                
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
                logger.warn { "Failed to register agent: ${agent.id} (${agent.name}) - Agent already exists" }
                
                CallToolResult(
                    content = listOf(
                        TextContent("Agent with ID ${agent.id} already exists")
                    )
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error registering agent: ${e.message}"
            logger.error(e) { errorMessage }
            
            CallToolResult(
                content = listOf(
                    TextContent(errorMessage)
                )
            )
        }
    }
}
