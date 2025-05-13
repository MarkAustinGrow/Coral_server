// Initialize Socket.io connection
const socket = io();

// Global variables
let messageTypesChart = null;
let communicationChart = null;
let selectedThreadId = '';
let agents = [];
let threads = [];
let messages = [];

// DOM elements
const agentCountElement = document.getElementById('agent-count');
const threadCountElement = document.getElementById('thread-count');
const messageCountElement = document.getElementById('message-count');
const recentActivityElement = document.getElementById('recent-activity');
const agentsListElement = document.getElementById('agents-list');
const threadsListElement = document.getElementById('threads-list');
const messagesContainerElement = document.getElementById('messages-container');
const threadFilterElement = document.getElementById('thread-filter');
const messageTypesChartElement = document.getElementById('message-types-chart');
const communicationChartElement = document.getElementById('communication-chart');

// Modal elements
const messageModal = new bootstrap.Modal(document.getElementById('messageModal'));
const modalMessageId = document.getElementById('modal-message-id');
const modalThreadId = document.getElementById('modal-thread-id');
const modalSenderId = document.getElementById('modal-sender-id');
const modalReceiverId = document.getElementById('modal-receiver-id');
const modalType = document.getElementById('modal-type');
const modalTimestamp = document.getElementById('modal-timestamp');
const modalContent = document.getElementById('modal-content');

// Initialize the application
function init() {
    // Fetch initial data
    fetchAgents();
    fetchThreads();
    fetchMessages();
    fetchStats();

    // Set up event listeners
    threadFilterElement.addEventListener('change', handleThreadFilterChange);

    // Set up Socket.io event listeners
    socket.on('agents_update', handleAgentsUpdate);
    socket.on('threads_update', handleThreadsUpdate);
    socket.on('message_update', handleMessageUpdate);
    socket.on('mentions_update', handleMentionsUpdate);

    // Initialize charts
    initializeCharts();
}

// Fetch agents from the API
function fetchAgents() {
    fetch('/api/agents')
        .then(response => response.json())
        .then(data => {
            agents = data;
            renderAgents();
        })
        .catch(error => console.error('Error fetching agents:', error));
}

// Fetch threads from the API
function fetchThreads() {
    fetch('/api/threads')
        .then(response => response.json())
        .then(data => {
            threads = data;
            renderThreads();
            updateThreadFilter();
        })
        .catch(error => console.error('Error fetching threads:', error));
}

// Fetch messages from the API
function fetchMessages(threadId = '') {
    const url = threadId ? `/api/messages?thread_id=${threadId}` : '/api/messages';
    fetch(url)
        .then(response => response.json())
        .then(data => {
            messages = data;
            renderMessages();
            updateCommunicationChart();
        })
        .catch(error => console.error('Error fetching messages:', error));
}

// Fetch statistics from the API
function fetchStats() {
    fetch('/api/stats')
        .then(response => response.json())
        .then(data => {
            updateStats(data);
            updateMessageTypesChart(data.message_types);
        })
        .catch(error => console.error('Error fetching stats:', error));
}

// Render agents in the UI
function renderAgents() {
    agentsListElement.innerHTML = '';
    
    if (agents.length === 0) {
        agentsListElement.innerHTML = '<div class="list-group-item">No agents connected</div>';
        return;
    }
    
    agents.forEach(agent => {
        const agentElement = document.createElement('div');
        agentElement.className = 'list-group-item';
        agentElement.innerHTML = `
            <div>
                <span class="agent-status ${agent.status}"></span>
                <span class="agent-id">${agent.id}</span>
                <div class="agent-description">${agent.description || 'No description'}</div>
            </div>
            <div class="agent-time">${formatDate(agent.last_seen)}</div>
        `;
        agentsListElement.appendChild(agentElement);
    });
}

// Render threads in the UI
function renderThreads() {
    threadsListElement.innerHTML = '';
    
    if (threads.length === 0) {
        threadsListElement.innerHTML = '<div class="list-group-item">No threads available</div>';
        return;
    }
    
    threads.forEach(thread => {
        const threadElement = document.createElement('div');
        threadElement.className = `list-group-item ${selectedThreadId === thread.id ? 'active' : ''}`;
        threadElement.innerHTML = `
            <div>
                <div class="thread-name">${thread.name || `Thread ${thread.id.substring(0, 8)}`}</div>
                <div class="thread-id">ID: ${thread.id.substring(0, 8)}...</div>
            </div>
            <div class="thread-time">${formatDate(thread.created_at)}</div>
        `;
        threadElement.addEventListener('click', () => {
            selectedThreadId = thread.id;
            renderThreads(); // Re-render to update active state
            fetchMessages(thread.id);
        });
        threadsListElement.appendChild(threadElement);
    });
}

// Render messages in the UI
function renderMessages() {
    messagesContainerElement.innerHTML = '';
    
    if (messages.length === 0) {
        messagesContainerElement.innerHTML = '<div class="alert alert-info">No messages available</div>';
        return;
    }
    
    messages.forEach(message => {
        const messageElement = document.createElement('div');
        messageElement.className = 'card message-card';
        
        // Try to parse content if it's a string
        let content = message.content;
        let displayContent = '';
        try {
            if (typeof content === 'string') {
                content = JSON.parse(content);
            }
            displayContent = JSON.stringify(content, null, 2);
        } catch (e) {
            displayContent = content;
        }
        
        // Truncate content for display
        const truncatedContent = displayContent.length > 200 
            ? displayContent.substring(0, 200) + '...' 
            : displayContent;
        
        messageElement.innerHTML = `
            <div class="card-header">
                <div>
                    <span class="message-sender">${message.sender_id || 'Unknown'}</span>
                    ${message.receiver_id ? `<span class="text-muted">→</span> <span class="message-receiver">${message.receiver_id}</span>` : ''}
                </div>
                <div>
                    <span class="message-type ${message.type}">${message.type || 'unknown'}</span>
                    <span class="message-time">${formatDate(message.timestamp)}</span>
                </div>
            </div>
            <div class="card-body">
                <pre class="message-content">${truncatedContent}</pre>
            </div>
        `;
        
        messageElement.addEventListener('click', () => {
            showMessageDetails(message);
        });
        
        messagesContainerElement.appendChild(messageElement);
    });
}

// Update thread filter dropdown
function updateThreadFilter() {
    // Save current selection
    const currentValue = threadFilterElement.value;
    
    // Clear options except the first one
    while (threadFilterElement.options.length > 1) {
        threadFilterElement.remove(1);
    }
    
    // Add thread options
    threads.forEach(thread => {
        const option = document.createElement('option');
        option.value = thread.id;
        option.textContent = thread.name || `Thread ${thread.id.substring(0, 8)}`;
        threadFilterElement.appendChild(option);
    });
    
    // Restore selection if possible
    if (currentValue && threads.some(t => t.id === currentValue)) {
        threadFilterElement.value = currentValue;
    }
}

// Update statistics in the UI
function updateStats(stats) {
    agentCountElement.textContent = stats.agent_count || 0;
    threadCountElement.textContent = stats.thread_count || 0;
    messageCountElement.textContent = stats.message_count || 0;
    recentActivityElement.textContent = stats.recent_activity || 0;
}

// Initialize charts
function initializeCharts() {
    // Message types chart
    messageTypesChart = new Chart(messageTypesChartElement, {
        type: 'doughnut',
        data: {
            labels: [],
            datasets: [{
                data: [],
                backgroundColor: [
                    '#0d6efd',
                    '#28a745',
                    '#dc3545',
                    '#ffc107',
                    '#6c757d'
                ]
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'bottom'
                }
            }
        }
    });
    
    // Communication chart
    communicationChart = new Chart(communicationChartElement, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Messages',
                data: [],
                borderColor: '#0d6efd',
                tension: 0.1,
                fill: false
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: {
                    title: {
                        display: true,
                        text: 'Time'
                    }
                },
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Message Count'
                    }
                }
            }
        }
    });
}

// Update message types chart
function updateMessageTypesChart(messageTypes) {
    if (!messageTypesChart) return;
    
    const labels = Object.keys(messageTypes);
    const data = Object.values(messageTypes);
    
    messageTypesChart.data.labels = labels;
    messageTypesChart.data.datasets[0].data = data;
    messageTypesChart.update();
}

// Update communication chart
function updateCommunicationChart() {
    if (!communicationChart || messages.length === 0) return;
    
    // Group messages by time (hourly)
    const messagesByHour = {};
    messages.forEach(message => {
        const date = new Date(message.timestamp);
        const hour = date.toISOString().substring(0, 13); // YYYY-MM-DDTHH
        messagesByHour[hour] = (messagesByHour[hour] || 0) + 1;
    });
    
    // Sort by time
    const sortedHours = Object.keys(messagesByHour).sort();
    
    // Format labels
    const labels = sortedHours.map(hour => {
        const date = new Date(hour);
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    });
    
    // Get data
    const data = sortedHours.map(hour => messagesByHour[hour]);
    
    // Update chart
    communicationChart.data.labels = labels;
    communicationChart.data.datasets[0].data = data;
    communicationChart.update();
}

// Show message details in modal
function showMessageDetails(message) {
    // Try to parse content if it's a string
    let content = message.content;
    let displayContent = '';
    try {
        if (typeof content === 'string') {
            content = JSON.parse(content);
        }
        displayContent = JSON.stringify(content, null, 2);
    } catch (e) {
        displayContent = content;
    }
    
    modalMessageId.textContent = message.id;
    modalThreadId.textContent = message.thread_id;
    modalSenderId.textContent = message.sender_id || 'Unknown';
    modalReceiverId.textContent = message.receiver_id || 'None';
    modalType.textContent = message.type || 'unknown';
    modalTimestamp.textContent = formatDate(message.timestamp);
    modalContent.textContent = displayContent;
    
    messageModal.show();
}

// Handle thread filter change
function handleThreadFilterChange() {
    const threadId = threadFilterElement.value;
    selectedThreadId = threadId;
    fetchMessages(threadId);
    renderThreads();
}

// Handle agents update from Socket.io
function handleAgentsUpdate(data) {
    agents = data;
    renderAgents();
    fetchStats();
}

// Handle threads update from Socket.io
function handleThreadsUpdate(data) {
    threads = data;
    renderThreads();
    updateThreadFilter();
    fetchStats();
}

// Handle message update from Socket.io
function handleMessageUpdate(message) {
    // Add message to the list if it's for the selected thread or no thread is selected
    if (!selectedThreadId || message.thread_id === selectedThreadId) {
        messages.unshift(message);
        renderMessages();
    }
    fetchStats();
}

// Handle mentions update from Socket.io
function handleMentionsUpdate() {
    // Refresh messages if a thread is selected
    if (selectedThreadId) {
        fetchMessages(selectedThreadId);
    } else {
        fetchMessages();
    }
    fetchStats();
}

// Format date for display
function formatDate(dateString) {
    if (!dateString) return 'Unknown';
    
    const date = new Date(dateString);
    
    // Check if date is valid
    if (isNaN(date.getTime())) return dateString;
    
    // If date is today, show only time
    const today = new Date();
    if (date.toDateString() === today.toDateString()) {
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }
    
    // Otherwise show date and time
    return date.toLocaleString([], { 
        year: 'numeric', 
        month: 'short', 
        day: 'numeric', 
        hour: '2-digit', 
        minute: '2-digit' 
    });
}

// Initialize the application when the DOM is loaded
document.addEventListener('DOMContentLoaded', init);
