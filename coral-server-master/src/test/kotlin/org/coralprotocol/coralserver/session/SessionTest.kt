package org.coralprotocol.coralserver.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.models.Agent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionTest {
    private lateinit var session: CoralAgentGraphSession

    @BeforeEach
    fun setup() {
        // Create a new session for each test
        session = CoralAgentGraphSession("test-session", "test-app", "test-key")
        // Clear any existing data
        session.clearAll()
    }

    @Test
    fun `test agent registration`() {
        // Register a new agent
        val agent = Agent(id = "agent1", name = "Test Agent 1")
        val success = session.registerAgent(agent)

        // Verify agent was registered
        assertTrue(success)
        assertEquals(agent, session.getAgent("agent1"))

        // Try to register the same agent again
        val duplicateSuccess = session.registerAgent(agent)
        assertFalse(duplicateSuccess)
    }

    @Test
    fun `test agent registration with description`() {
        // Register a new agent with description
        val agent = Agent(id = "agent2", name = "Test Agent 2", description = "This agent is responsible for testing")
        val success = session.registerAgent(agent)

        // Verify agent was registered with description
        assertTrue(success)
        val retrievedAgent = session.getAgent("agent2")
        assertEquals(agent, retrievedAgent)
        assertEquals("This agent is responsible for testing", retrievedAgent?.description)
    }

    @Test
    fun `test thread creation`() {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant1 = Agent(id = "participant1", name = "Participant 1")
        val participant2 = Agent(id = "participant2", name = "Participant 2")

        session.registerAgent(creator)
        session.registerAgent(participant1)
        session.registerAgent(participant2)

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant1", "participant2")
        )

        // Verify thread was created
        assertNotNull(thread)
        assertEquals("Test Thread", thread?.name)
        assertEquals("creator", thread?.creatorId)
        assertTrue(thread?.participants?.contains("creator") ?: false)
        assertTrue(thread?.participants?.contains("participant1") ?: false)
        assertTrue(thread?.participants?.contains("participant2") ?: false)
        assertEquals(3, thread?.participants?.size)
    }

    @Test
    fun `test adding and removing participants`() {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant1 = Agent(id = "participant1", name = "Participant 1")
        val participant2 = Agent(id = "participant2", name = "Participant 2")
        val participant3 = Agent(id = "participant3", name = "Participant 3")

        session.registerAgent(creator)
        session.registerAgent(participant1)
        session.registerAgent(participant2)
        session.registerAgent(participant3)

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant1")
        )

        // Add a participant
        val addSuccess = session.addParticipantToThread(
            threadId = thread?.id ?: "",
            participantId = "participant2"
        )

        // Verify participant was added
        assertTrue(addSuccess)
        val updatedThread = session.getThread(thread?.id ?: "")
        assertTrue(updatedThread?.participants?.contains("participant2") ?: false)

        // Remove a participant
        val removeSuccess = session.removeParticipantFromThread(
            threadId = thread?.id ?: "",
            participantId = "participant1"
        )

        // Verify participant was removed
        assertTrue(removeSuccess)
        val finalThread = session.getThread(thread?.id ?: "")
        assertFalse(finalThread?.participants?.contains("participant1") ?: true)
    }

    @Test
    fun `test sending messages and closing thread`() {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant = Agent(id = "participant", name = "Participant")

        session.registerAgent(creator)
        session.registerAgent(participant)

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant")
        )

        // Send a message
        val message = session.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "Hello, world!",
            mentions = listOf("participant")
        )

        // Verify message was sent
        assertNotNull(message)
        assertEquals("Hello, world!", message?.content)
        assertEquals("creator", message?.senderId)
        assertEquals(thread?.id, message?.threadId)
        assertTrue(message?.mentions?.contains("participant") ?: false)

        // Close the thread
        val closeSuccess = session.closeThread(
            threadId = thread?.id ?: "",
            summary = "Thread completed"
        )

        // Verify thread was closed
        assertTrue(closeSuccess)
        val closedThread = session.getThread(thread?.id ?: "")
        assertTrue(closedThread?.isClosed ?: false)
        assertEquals("Thread completed", closedThread?.summary)

        // Try to send a message to a closed thread
        val failedMessage = session.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "This should fail",
            mentions = listOf()
        )

        // Verify message was not sent
        assertNull(failedMessage)
    }

    @Test
    fun `test waiting for mentions`() = runBlocking {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant = Agent(id = "participant", name = "Participant")

        session.registerAgent(creator)
        session.registerAgent(participant)

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant")
        )

        // Launch a coroutine to wait for mentions
        val waitJob = launch(Dispatchers.Default) {
            val messages = session.waitForMentions(
                agentId = "participant",
                timeoutMs = 5000
            )

            // Verify messages were received
            assertFalse(messages.isEmpty())
            assertEquals(1, messages.size)
            assertEquals("Hello, participant!", messages[0].content)
        }

        // Wait a bit to ensure the wait operation has started
        delay(100)

        // Send a message with a mention
        session.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "Hello, participant!",
            mentions = listOf("participant")
        )

        // Wait for the job to complete
        waitJob.join()
    }

    @Test
    fun `test waiting for mentions with timeout`() = runBlocking {
        // Register an agent
        val agent = Agent(id = "agent", name = "Test Agent")
        session.registerAgent(agent)

        // Wait for mentions with a short timeout
        val messages = session.waitForMentions(
            agentId = "agent",
            timeoutMs = 100
        )

        // Verify no messages were received
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test listing all agents`() {
        // Register multiple agents
        val agent1 = Agent(id = "agent1", name = "Agent 1")
        val agent2 = Agent(id = "agent2", name = "Agent 2")
        val agent3 = Agent(id = "agent3", name = "Agent 3")

        session.registerAgent(agent1)
        session.registerAgent(agent2)
        session.registerAgent(agent3)

        // Get all agents
        val agents = session.getAllAgents()

        // Verify all agents are returned
        assertEquals(3, agents.size)
        assertTrue(agents.contains(agent1))
        assertTrue(agents.contains(agent2))
        assertTrue(agents.contains(agent3))
    }

    @Test
    fun `test waiting for agent count`() = runBlocking {
        // Register some agents
        val agent1 = Agent(id = "agent1", name = "Agent 1")
        val agent2 = Agent(id = "agent2", name = "Agent 2")
        
        session.registerAgent(agent1)
        session.registerAgent(agent2)
        
        // Verify current count
        assertEquals(2, session.getRegisteredAgentsCount())
        
        // Launch a coroutine to wait for more agents
        val waitJob = launch(Dispatchers.Default) {
            val result = session.waitForAgentCount(
                targetCount = 3,
                timeoutMs = 5000
            )
            
            // Verify wait was successful
            assertTrue(result)
            assertEquals(3, session.getRegisteredAgentsCount())
        }
        
        // Wait a bit to ensure the wait operation has started
        delay(100)
        
        // Register another agent
        val agent3 = Agent(id = "agent3", name = "Agent 3")
        session.registerAgent(agent3)
        
        // Wait for the job to complete
        waitJob.join()
    }
    
    @Test
    fun `test waiting for agent count with timeout`() = runBlocking {
        // Register some agents
        val agent1 = Agent(id = "agent1", name = "Agent 1")
        session.registerAgent(agent1)
        
        // Wait for more agents with a short timeout
        val result = session.waitForAgentCount(
            targetCount = 3,
            timeoutMs = 100
        )
        
        // Verify wait timed out
        assertFalse(result)
        assertEquals(1, session.getRegisteredAgentsCount())
    }
    
    @Test
    fun `test get threads for agent`() {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant1 = Agent(id = "participant1", name = "Participant 1")
        val participant2 = Agent(id = "participant2", name = "Participant 2")
        
        session.registerAgent(creator)
        session.registerAgent(participant1)
        session.registerAgent(participant2)
        
        // Create threads
        val thread1 = session.createThread(
            name = "Thread 1",
            creatorId = "creator",
            participantIds = listOf("participant1")
        )
        
        val thread2 = session.createThread(
            name = "Thread 2",
            creatorId = "creator",
            participantIds = listOf("participant1", "participant2")
        )
        
        val thread3 = session.createThread(
            name = "Thread 3",
            creatorId = "participant2",
            participantIds = listOf("creator")
        )
        
        // Get threads for participant1
        val threadsForParticipant1 = session.getThreadsForAgent("participant1")
        
        // Verify correct threads are returned
        assertEquals(2, threadsForParticipant1.size)
        assertTrue(threadsForParticipant1.contains(thread1))
        assertTrue(threadsForParticipant1.contains(thread2))
        assertFalse(threadsForParticipant1.contains(thread3))
        
        // Get threads for participant2
        val threadsForParticipant2 = session.getThreadsForAgent("participant2")
        
        // Verify correct threads are returned
        assertEquals(2, threadsForParticipant2.size)
        assertTrue(threadsForParticipant2.contains(thread2))
        assertTrue(threadsForParticipant2.contains(thread3))
        assertFalse(threadsForParticipant2.contains(thread1))
    }
}