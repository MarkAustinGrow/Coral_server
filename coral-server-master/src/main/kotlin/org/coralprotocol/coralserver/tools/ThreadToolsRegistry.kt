package org.coralprotocol.coralserver.tools

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add all thread-based tools to a server.
 */
fun CoralAgentIndividualMcp.addThreadTools() {
    logger.debug { "Adding tools to server for agent: $connectedAgentId" }
    
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
    
    logger.debug { "All tools added to server for agent: $connectedAgentId" }
}
