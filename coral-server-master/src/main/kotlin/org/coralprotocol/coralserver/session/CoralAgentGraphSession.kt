package org.coralprotocol.coralserver.session

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.Thread
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Session class to hold stateful information for a specific application and privacy key.
 * [devRequiredAgentStartCount] is the number of agents that need to register before the session can proceed. This is for devmode only.
 * TODO: Implement a mechanism for waiting for specific agents to register for production mode.
 */
class CoralAgentGraphSession(
    val id: String,
    val applicationId: String,
    val privacyKey: String,
    val coralAgentConnections: MutableList<CoralAgentIndividualMcp> = mutableListOf(),
    var devRequiredAgentStartCount: Int = 0
) {
    private val agents = ConcurrentHashMap<String, Agent>()

    private val threads = ConcurrentHashMap<String, Thread>()

    private val agentNotifications = ConcurrentHashMap<String, CompletableDeferred<List<Message>>>()

    private val lastReadMessageIndex = ConcurrentHashMap<Pair<String, String>, Int>()

    private val registeredAgentsCount = AtomicInteger(0)

    private val agentCountNotifications = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    fun clearAll() {
        logger.info { "Clearing all session data for session $id" }
        agents.clear()
        threads.clear()
        agentNotifications.clear()
        lastReadMessageIndex.clear()
        registeredAgentsCount.set(0)
        agentCountNotifications.clear()
        logger.debug { "Session data cleared for session $id" }
    }

    fun registerAgent(agent: Agent): Boolean {
        logger.info { "Attempting to register agent: ${agent.id} (${agent.name}) in session $id" }
        
        if (agents.containsKey(agent.id)) {
            logger.warn { "Agent ${agent.id} already exists in session $id, registration failed" }
            return false
        }
        
        agents[agent.id] = agent
        logger.info { "Agent ${agent.id} (${agent.name}) registered successfully in session $id" }

        val newCount = registeredAgentsCount.incrementAndGet()
        logger.debug { "Total registered agents in session $id: $newCount" }

        // Notify any waiters that the agent count has changed
        var notifiedCount = 0
        agentCountNotifications.entries.removeIf { (targetCount, deferred) ->
            if (newCount >= targetCount && !deferred.isCompleted) {
                deferred.complete(true)
                notifiedCount++
                true
            } else {
                false
            }
        }
        
        if (notifiedCount > 0) {
            logger.debug { "Notified $notifiedCount waiters about new agent count: $newCount" }
        }

        return true
    }

    fun getRegisteredAgentsCount(): Int {
        val count = registeredAgentsCount.get()
        logger.debug { "Current registered agent count for session $id: $count" }
        return count
    }

    suspend fun waitForAgentCount(targetCount: Int, timeoutMs: Long): Boolean {
        val currentCount = registeredAgentsCount.get()
        logger.info { "Waiting for agent count to reach $targetCount in session $id (current count: $currentCount, timeout: ${timeoutMs}ms)" }
        
        if (currentCount >= targetCount) {
            logger.debug { "Agent count already reached target: $currentCount >= $targetCount" }
            return true
        }

        val deferred = CompletableDeferred<Boolean>()
        agentCountNotifications[targetCount] = deferred
        logger.debug { "Added waiter for agent count $targetCount" }

        val result = withTimeoutOrNull(timeoutMs) {
            logger.debug { "Awaiting agent count to reach $targetCount..." }
            deferred.await()
        } ?: false

        if (!result) {
            logger.warn { "Timeout waiting for agent count to reach $targetCount (current count: ${registeredAgentsCount.get()})" }
            agentCountNotifications.remove(targetCount)
        } else {
            logger.info { "Agent count reached target: $targetCount" }
        }

        return result
    }

    fun getAgent(agentId: String): Agent? {
        val agent = agents[agentId]
        if (agent == null) {
            logger.debug { "Agent $agentId not found in session $id" }
        } else {
            logger.debug { "Retrieved agent $agentId (${agent.name}) from session $id" }
        }
        return agent
    }

    fun getAllAgents(): List<Agent> {
        val agentList = agents.values.toList()
        logger.debug { "Retrieved all agents from session $id: ${agentList.size} agents" }
        return agentList
    }

    fun createThread(name: String, creatorId: String, participantIds: List<String>): Thread {
        logger.info { "Creating thread '$name' by agent $creatorId with participants: ${participantIds.joinToString()}" }
        
        val creator = agents[creatorId]
        if (creator == null) {
            logger.error { "Creator agent $creatorId not found in session $id" }
            throw IllegalArgumentException("Creator agent not found")
        }
        
        // Filter out invalid participants
        val invalidParticipants = participantIds.filter { !agents.containsKey(it) }
        if (invalidParticipants.isNotEmpty()) {
            logger.warn { "Some participants not found in session $id: ${invalidParticipants.joinToString()}" }
        }
        
        val validParticipants = participantIds.filter { agents.containsKey(it) }.toMutableList()
        logger.debug { "Valid participants: ${validParticipants.joinToString()}" }

        // Make sure creator is included in participants
        if (!validParticipants.contains(creatorId)) {
            validParticipants.add(creatorId)
            logger.debug { "Added creator $creatorId to participants list" }
        }

        val thread = Thread(
            name = name,
            creatorId = creatorId,
            participants = validParticipants
        )
        threads[thread.id] = thread
        logger.info { "Thread created with ID: ${thread.id}, name: $name, participants: ${validParticipants.size}" }
        return thread
    }

    fun getThread(threadId: String): Thread? = threads[threadId]

    fun getThreadsForAgent(agentId: String): List<Thread> {
        return threads.values.filter { it.participants.contains(agentId) }
    }

    fun addParticipantToThread(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false
        val agent = agents[participantId] ?: return false

        if (thread.isClosed) return false

        if (!thread.participants.contains(participantId)) {
            thread.participants.add(participantId)
            lastReadMessageIndex[Pair(participantId, threadId)] = thread.messages.size
        }
        return true
    }

    fun removeParticipantFromThread(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false

        if (thread.isClosed) return false

        return thread.participants.remove(participantId)
    }

    fun closeThread(threadId: String, summary: String): Boolean {
        val thread = threads[threadId] ?: return false

        thread.isClosed = true
        thread.summary = summary

        val closeMessage = Message(
            threadId = threadId,
            senderId = "system",
            content = "Thread closed: $summary"
        )
        thread.messages.add(closeMessage)
        notifyMentionedAgents(closeMessage)

        return true
    }

    fun getColorForSenderId(senderId: String): String {
        val colors = listOf(
            "#FF5733", "#33FF57", "#3357FF", "#F3FF33", "#FF33F3",
            "#33FFF3", "#FF8033", "#8033FF", "#33FF80", "#FF3380"
        )
        val hash = senderId.hashCode()
        val index = Math.abs(hash) % colors.size
        return colors[index]
    }

    fun sendMessage(threadId: String, senderId: String, content: String, mentions: List<String> = emptyList()): Message? {
        logger.info { "Sending message from agent $senderId to thread $threadId with mentions: ${mentions.joinToString()}" }
        
        val thread = threads[threadId]
        if (thread == null) {
            logger.warn { "Thread $threadId not found in session $id" }
            return null
        }
        
        val sender = agents[senderId]
        if (sender == null) {
            logger.warn { "Sender agent $senderId not found in session $id" }
            return null
        }

        if (thread.isClosed) {
            logger.warn { "Cannot send message to closed thread $threadId" }
            return null
        }

        if (!thread.participants.contains(senderId)) {
            logger.warn { "Sender $senderId is not a participant in thread $threadId" }
            return null
        }

        // Filter out invalid mentions
        val invalidMentions = mentions.filter { !thread.participants.contains(it) }
        if (invalidMentions.isNotEmpty()) {
            logger.warn { "Some mentioned agents are not participants in thread $threadId: ${invalidMentions.joinToString()}" }
        }
        
        val validMentions = mentions.filter { thread.participants.contains(it) }
        logger.debug { "Valid mentions: ${validMentions.joinToString()}" }

        val message = Message(
            threadId = threadId,
            senderId = senderId,
            content = content,
            mentions = validMentions
        )
        thread.messages.add(message)
        logger.debug { "Added message to thread $threadId: ${message.id}" }

        notifyMentionedAgents(message)
        logger.debug { "Notified mentioned agents about new message" }

        return message
    }

    fun notifyMentionedAgents(message: Message) {
        logger.debug { "Notifying agents about message ${message.id} in thread ${message.threadId}" }
        
        if (message.senderId == "system") {
            logger.debug { "System message, notifying all thread participants" }
            val thread = threads[message.threadId]
            if (thread == null) {
                logger.warn { "Thread ${message.threadId} not found, cannot notify participants" }
                return
            }
            
            var notifiedCount = 0
            thread.participants.forEach { participantId ->
                val deferred = agentNotifications[participantId]
                if (deferred != null && !deferred.isCompleted) {
                    deferred.complete(listOf(message))
                    notifiedCount++
                }
            }
            
            logger.debug { "Notified $notifiedCount participants about system message" }
            return
        }

        var notifiedCount = 0
        message.mentions.forEach { mentionId ->
            val deferred = agentNotifications[mentionId]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(listOf(message))
                notifiedCount++
                logger.debug { "Notified agent $mentionId about mention in message ${message.id}" }
            }
        }
        
        logger.debug { "Notified $notifiedCount mentioned agents about message ${message.id}" }
    }

    suspend fun waitForMentions(agentId: String, timeoutMs: Long): List<Message> {
        val agent = agents[agentId] ?: return emptyList()

        val unreadMessages = getUnreadMessagesForAgent(agentId)
        if (unreadMessages.isNotEmpty()) {
            updateLastReadIndices(agentId, unreadMessages)
            return unreadMessages
        }

        val deferred = CompletableDeferred<List<Message>>()
        agentNotifications[agentId] = deferred

        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: emptyList()

        agentNotifications.remove(agentId)

        updateLastReadIndices(agentId, result)

        return result
    }

    fun getUnreadMessagesForAgent(agentId: String): List<Message> {
        val agent = agents[agentId] ?: return emptyList()

        val result = mutableListOf<Message>()

        val agentThreads = getThreadsForAgent(agentId)

        for (thread in agentThreads) {
            val lastReadIndex = lastReadMessageIndex[Pair(agentId, thread.id)] ?: 0

            val unreadMessages = thread.messages.subList(lastReadIndex, thread.messages.size)

            result.addAll(unreadMessages.filter {
                it.mentions.contains(agentId) || it.senderId == "system" 
            })
        }

        return result
    }

    fun updateLastReadIndices(agentId: String, messages: List<Message>) {
        val messagesByThread = messages.groupBy { it.threadId }

        for ((threadId, threadMessages) in messagesByThread) {
            val thread = threads[threadId] ?: continue
            val messageIndices = threadMessages.map { thread.messages.indexOf(it) }
            if (messageIndices.isNotEmpty()) {
                val maxIndex = messageIndices.maxOrNull() ?: continue
                lastReadMessageIndex[Pair(agentId, threadId)] = maxIndex + 1
            }
        }
    }
}
