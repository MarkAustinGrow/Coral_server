// Global state
let currentSessionId = null;
let currentThreadId = null;
let refreshInterval = null;

// DOM elements
const sessionSelector = document.getElementById('session-selector');
const sessionDetails = document.getElementById('session-details');
const agentsList = document.getElementById('agents-list');
const threadsList = document.getElementById('threads-list');
const messagesList = document.getElementById('messages-list');
const communicationGraph = document.getElementById('communication-graph');

// Stats elements
const sessionsCount = document.getElementById('sessions-count').querySelector('.stat-value');
const agentsCount = document.getElementById('agents-count').querySelector('.stat-value');
const threadsCount = document.getElementById('threads-count').querySelector('.stat-value');
const messagesCount = document.getElementById('messages-count').querySelector('.stat-value');

// Initialize the dashboard
document.addEventListener('DOMContentLoaded', () => {
    // Load initial data
    loadDashboardData();
    
    // Set up event listeners
    sessionSelector.addEventListener('change', handleSessionChange);
    
    // Set up auto-refresh
    refreshInterval = setInterval(refreshData, 5000);
});

// Load dashboard data
async function loadDashboardData() {
    try {
        const response = await fetch('/api/dashboard');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        updateDashboardStats(data);
        loadSessions(data.sessions);
    } catch (error) {
        console.error('Error loading dashboard data:', error);
        showError('Failed to load dashboard data. Please try again later.');
    }
}

// Update dashboard stats
function updateDashboardStats(data) {
    sessionsCount.textContent = data.sessions.length;
    agentsCount.textContent = data.totalAgents;
    threadsCount.textContent = data.totalThreads;
    messagesCount.textContent = data.totalMessages;
}

// Load sessions into the selector
function loadSessions(sessions) {
    // Clear existing options except the default one
    while (sessionSelector.options.length > 1) {
        sessionSelector.remove(1);
    }
    
    // Add new options
    sessions.forEach(session => {
        const option = document.createElement('option');
        option.value = session.id;
        option.textContent = `${session.id} (${session.applicationId})`;
        sessionSelector.appendChild(option);
    });
    
    // If we had a selected session, try to reselect it
    if (currentSessionId) {
        sessionSelector.value = currentSessionId;
        // If the session no longer exists, reset the current session
        if (sessionSelector.value !== currentSessionId) {
            currentSessionId = null;
            resetSessionView();
        }
    }
}

// Handle session change
function handleSessionChange() {
    currentSessionId = sessionSelector.value;
    
    if (currentSessionId) {
        loadSessionDetails(currentSessionId);
        loadAgents(currentSessionId);
        loadThreads(currentSessionId);
        // Reset thread view
        currentThreadId = null;
        resetThreadView();
    } else {
        resetSessionView();
    }
}

// Load session details
async function loadSessionDetails(sessionId) {
    try {
        const response = await fetch(`/api/sessions/${sessionId}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const session = await response.json();
        
        sessionDetails.innerHTML = `
            <div class="session-info">
                <p><strong>ID:</strong> ${session.id}</p>
                <p><strong>Application:</strong> ${session.applicationId}</p>
                <p><strong>Agents:</strong> ${session.agentCount}</p>
                <p><strong>Threads:</strong> ${session.threadCount}</p>
            </div>
        `;
    } catch (error) {
        console.error('Error loading session details:', error);
        sessionDetails.innerHTML = '<p class="error">Failed to load session details</p>';
    }
}

// Load agents for a session
async function loadAgents(sessionId) {
    try {
        const response = await fetch(`/api/sessions/${sessionId}/agents`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const agents = await response.json();
        
        if (agents.length === 0) {
            agentsList.innerHTML = '<p>No agents registered in this session</p>';
            return;
        }
        
        let html = '<div class="agents-container">';
        agents.forEach(agent => {
            html += `
                <div class="list-item agent-item" data-id="${agent.id}">
                    <div class="agent-name">${agent.name}</div>
                    <div class="agent-id">ID: ${agent.id}</div>
                    ${agent.description ? `<div class="agent-description">${agent.description}</div>` : ''}
                </div>
            `;
        });
        html += '</div>';
        
        agentsList.innerHTML = html;
        
        // Add event listeners to agent items
        document.querySelectorAll('.agent-item').forEach(item => {
            item.addEventListener('click', () => {
                // Highlight the selected agent
                document.querySelectorAll('.agent-item').forEach(i => i.classList.remove('active'));
                item.classList.add('active');
                
                // Filter threads by agent
                const agentId = item.dataset.id;
                highlightAgentThreads(agentId);
            });
        });
    } catch (error) {
        console.error('Error loading agents:', error);
        agentsList.innerHTML = '<p class="error">Failed to load agents</p>';
    }
}

// Load threads for a session
async function loadThreads(sessionId) {
    try {
        const response = await fetch(`/api/sessions/${sessionId}/threads`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const threads = await response.json();
        
        if (threads.length === 0) {
            threadsList.innerHTML = '<p>No threads in this session</p>';
            return;
        }
        
        let html = '<div class="threads-container">';
        threads.forEach(thread => {
            html += `
                <div class="list-item thread-item ${thread.isClosed ? 'closed' : ''}" data-id="${thread.id}" data-participants="${thread.participants.join(',')}">
                    <div class="thread-name">${thread.name}</div>
                    <div class="thread-info">
                        <span class="thread-id">ID: ${thread.id}</span>
                        <span class="thread-status">${thread.isClosed ? 'Closed' : 'Open'}</span>
                        <span class="thread-messages">${thread.messageCount} messages</span>
                    </div>
                </div>
            `;
        });
        html += '</div>';
        
        threadsList.innerHTML = html;
        
        // Add event listeners to thread items
        document.querySelectorAll('.thread-item').forEach(item => {
            item.addEventListener('click', () => {
                // Highlight the selected thread
                document.querySelectorAll('.thread-item').forEach(i => i.classList.remove('active'));
                item.classList.add('active');
                
                // Load messages for this thread
                currentThreadId = item.dataset.id;
                loadMessages(currentSessionId, currentThreadId);
            });
        });
        
        // Update communication graph
        updateCommunicationGraph(threads);
    } catch (error) {
        console.error('Error loading threads:', error);
        threadsList.innerHTML = '<p class="error">Failed to load threads</p>';
    }
}

// Load messages for a thread
async function loadMessages(sessionId, threadId) {
    try {
        const response = await fetch(`/api/sessions/${sessionId}/threads/${threadId}/messages`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const messages = await response.json();
        
        if (messages.length === 0) {
            messagesList.innerHTML = '<p>No messages in this thread</p>';
            return;
        }
        
        let html = '<div class="messages-container">';
        messages.forEach(message => {
            const date = new Date(message.timestamp);
            const formattedDate = date.toLocaleString();
            
            html += `
                <div class="message" data-id="${message.id}">
                    <div class="message-header">
                        <span class="message-sender">${message.senderId}</span>
                        <span class="message-time">${formattedDate}</span>
                    </div>
                    <div class="message-content">${message.content}</div>
                    ${message.mentions.length > 0 ? 
                        `<div class="message-mentions">Mentions: ${message.mentions.join(', ')}</div>` : 
                        ''}
                </div>
            `;
        });
        html += '</div>';
        
        messagesList.innerHTML = html;
    } catch (error) {
        console.error('Error loading messages:', error);
        messagesList.innerHTML = '<p class="error">Failed to load messages</p>';
    }
}

// Highlight threads that include a specific agent
function highlightAgentThreads(agentId) {
    document.querySelectorAll('.thread-item').forEach(item => {
        const participants = item.dataset.participants.split(',');
        if (participants.includes(agentId)) {
            item.classList.add('highlight');
        } else {
            item.classList.remove('highlight');
        }
    });
}

// Update the communication graph
function updateCommunicationGraph(threads) {
    // Clear previous graph
    communicationGraph.innerHTML = '';
    
    if (threads.length === 0) {
        communicationGraph.innerHTML = '<p>No threads available to visualize</p>';
        return;
    }
    
    // Create a canvas for the graph
    const canvas = document.createElement('canvas');
    communicationGraph.appendChild(canvas);
    
    // Collect data for the graph
    const agentInteractions = {};
    
    threads.forEach(thread => {
        thread.participants.forEach(agent1 => {
            if (!agentInteractions[agent1]) {
                agentInteractions[agent1] = {};
            }
            
            thread.participants.forEach(agent2 => {
                if (agent1 !== agent2) {
                    if (!agentInteractions[agent1][agent2]) {
                        agentInteractions[agent1][agent2] = 0;
                    }
                    agentInteractions[agent1][agent2]++;
                }
            });
        });
    });
    
    // Convert to chart data
    const agents = Object.keys(agentInteractions);
    const datasets = agents.map((agent, index) => {
        const data = agents.map(otherAgent => {
            return agentInteractions[agent][otherAgent] || 0;
        });
        
        return {
            label: agent,
            data: data,
            backgroundColor: getColor(index),
            borderColor: getColor(index),
            borderWidth: 1
        };
    });
    
    // Create the chart
    new Chart(canvas, {
        type: 'bar',
        data: {
            labels: agents,
            datasets: datasets
        },
        options: {
            responsive: true,
            scales: {
                x: {
                    stacked: true,
                    title: {
                        display: true,
                        text: 'Agents'
                    }
                },
                y: {
                    stacked: true,
                    title: {
                        display: true,
                        text: 'Interaction Count'
                    }
                }
            },
            plugins: {
                title: {
                    display: true,
                    text: 'Agent Interactions'
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const agent1 = context.dataset.label;
                            const agent2 = context.label;
                            const count = context.raw;
                            return `${agent1} → ${agent2}: ${count} interactions`;
                        }
                    }
                }
            }
        }
    });
}

// Reset session view
function resetSessionView() {
    sessionDetails.innerHTML = '<p>Select a session to view details</p>';
    agentsList.innerHTML = '<p>Select a session to view agents</p>';
    threadsList.innerHTML = '<p>Select a session to view threads</p>';
    messagesList.innerHTML = '<p>Select a thread to view messages</p>';
    communicationGraph.innerHTML = '<p>Select a session to view the communication graph</p>';
}

// Reset thread view
function resetThreadView() {
    messagesList.innerHTML = '<p>Select a thread to view messages</p>';
}

// Refresh data
function refreshData() {
    loadDashboardData();
    
    if (currentSessionId) {
        loadSessionDetails(currentSessionId);
        loadAgents(currentSessionId);
        loadThreads(currentSessionId);
        
        if (currentThreadId) {
            loadMessages(currentSessionId, currentThreadId);
        }
    }
}

// Show error message
function showError(message) {
    const errorDiv = document.createElement('div');
    errorDiv.className = 'error-message';
    errorDiv.textContent = message;
    
    document.body.appendChild(errorDiv);
    
    setTimeout(() => {
        errorDiv.remove();
    }, 5000);
}

// Get color for chart
function getColor(index) {
    const colors = [
        '#FF5733', '#33FF57', '#3357FF', '#F3FF33', '#FF33F3',
        '#33FFF3', '#FF8033', '#8033FF', '#33FF80', '#FF3380'
    ];
    
    return colors[index % colors.length];
}
