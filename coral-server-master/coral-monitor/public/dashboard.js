// Simplified dashboard.js
document.addEventListener('DOMContentLoaded', () => {
    // Fetch sessions
    fetch('/api/sessions')
        .then(response => {
            if (!response.ok) {
                throw new Error('API not available');
            }
            return response.json();
        })
        .then(sessions => {
            // Update session count
            document.getElementById('sessions-count').textContent = sessions.length || '0';
            
            // Populate session selector
            const sessionSelector = document.getElementById('session-selector');
            
            // Clear existing options except the default one
            while (sessionSelector.options.length > 1) {
                sessionSelector.remove(1);
            }
            
            if (sessions.length === 0) {
                // No sessions available
                return;
            }
            
            sessions.forEach(session => {
                const option = document.createElement('option');
                option.value = session.id;
                option.textContent = session.id;
                sessionSelector.appendChild(option);
            });
            
            // Add event listener to session selector
            sessionSelector.addEventListener('change', () => {
                const sessionId = sessionSelector.value;
                if (sessionId) {
                    fetchSessionDetails(sessionId);
                    fetchAgents(sessionId);
                    fetchThreads(sessionId);
                }
            });
        })
        .catch(error => {
            console.error('Error fetching sessions:', error);
            // Instead of showing "API Error", show "0"
            document.getElementById('sessions-count').textContent = '0';
        });
    
    // Function to fetch session details
    function fetchSessionDetails(sessionId) {
        fetch(`/api/sessions/${sessionId}`)
            .then(response => response.json())
            .then(session => {
                const sessionDetails = document.getElementById('session-details');
                sessionDetails.innerHTML = `
                    <p><strong>ID:</strong> ${session.id}</p>
                    <p><strong>Application:</strong> ${session.applicationId}</p>
                    <p><strong>Agents:</strong> ${session.agentCount}</p>
                    <p><strong>Threads:</strong> ${session.threadCount}</p>
                `;
            })
            .catch(error => {
                console.error('Error fetching session details:', error);
            });
    }
    
    // Function to fetch agents
    function fetchAgents(sessionId) {
        fetch(`/api/sessions/${sessionId}/agents`)
            .then(response => response.json())
            .then(agents => {
                document.getElementById('agents-count').textContent = agents.length || '0';
                
                const agentsList = document.getElementById('agents-list');
                if (agents.length === 0) {
                    agentsList.innerHTML = '<p>No agents registered in this session</p>';
                    return;
                }
                
                let html = '';
                agents.forEach(agent => {
                    html += `
                        <div class="list-item">
                            <div><strong>${agent.name}</strong></div>
                            <div>ID: ${agent.id}</div>
                            ${agent.description ? `<div>${agent.description}</div>` : ''}
                        </div>
                    `;
                });
                
                agentsList.innerHTML = html;
            })
            .catch(error => {
                console.error('Error fetching agents:', error);
                document.getElementById('agents-count').textContent = '0';
                const agentsList = document.getElementById('agents-list');
                agentsList.innerHTML = '<p>No agents registered in this session</p>';
            });
    }
    
    // Function to fetch threads
    function fetchThreads(sessionId) {
        fetch(`/api/sessions/${sessionId}/threads`)
            .then(response => response.json())
            .then(threads => {
                document.getElementById('threads-count').textContent = threads.length || '0';
                
                const threadsList = document.getElementById('threads-list');
                if (threads.length === 0) {
                    threadsList.innerHTML = '<p>No threads in this session</p>';
                    document.getElementById('messages-count').textContent = '0';
                    return;
                }
                
                let html = '';
                let totalMessages = 0;
                
                threads.forEach(thread => {
                    totalMessages += thread.messageCount;
                    html += `
                        <div class="list-item" data-thread-id="${thread.id}">
                            <div><strong>${thread.name}</strong></div>
                            <div>ID: ${thread.id}</div>
                            <div>Status: ${thread.isClosed ? 'Closed' : 'Open'}</div>
                            <div>Messages: ${thread.messageCount}</div>
                        </div>
                    `;
                });
                
                document.getElementById('messages-count').textContent = totalMessages;
                threadsList.innerHTML = html;
                
                // Add event listeners to thread items
                document.querySelectorAll('.list-item[data-thread-id]').forEach(item => {
                    item.addEventListener('click', () => {
                        const threadId = item.dataset.threadId;
                        fetchMessages(sessionId, threadId);
                    });
                });
            })
            .catch(error => {
                console.error('Error fetching threads:', error);
                document.getElementById('threads-count').textContent = '0';
                document.getElementById('messages-count').textContent = '0';
                const threadsList = document.getElementById('threads-list');
                threadsList.innerHTML = '<p>No threads in this session</p>';
            });
    }
    
    // Function to fetch messages
    function fetchMessages(sessionId, threadId) {
        fetch(`/api/sessions/${sessionId}/threads/${threadId}/messages`)
            .then(response => response.json())
            .then(messages => {
                const messagesList = document.getElementById('messages-list');
                if (!messages || messages.length === 0) {
                    messagesList.innerHTML = '<p>No messages in this thread</p>';
                    return;
                }
                
                let html = '';
                messages.forEach(message => {
                    const date = new Date(message.timestamp);
                    html += `
                        <div class="message">
                            <div class="message-header">
                                <span>${message.senderId}</span>
                                <span>${date.toLocaleString()}</span>
                            </div>
                            <div class="message-content">${message.content}</div>
                            ${message.mentions && message.mentions.length > 0 ? 
                                `<div class="message-mentions">Mentions: ${message.mentions.join(', ')}</div>` : 
                                ''}
                        </div>
                    `;
                });
                
                messagesList.innerHTML = html;
            })
            .catch(error => {
                console.error('Error fetching messages:', error);
                const messagesList = document.getElementById('messages-list');
                messagesList.innerHTML = '<p>No messages in this thread</p>';
            });
    }
});
