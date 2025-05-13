import asyncio
import json
import logging
import os
import sqlite3
import threading
import time
import uuid
import requests
from datetime import datetime
from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS
from flask_socketio import SocketIO
import urllib.parse

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Flask app setup
app = Flask(__name__, static_folder='web')
CORS(app)
socketio = SocketIO(app, cors_allowed_origins="*")

# Database setup
DB_PATH = 'coral_monitor.db'
conn = sqlite3.connect(DB_PATH, check_same_thread=False)
cursor = conn.cursor()

# Create tables
cursor.execute('''
CREATE TABLE IF NOT EXISTS agents (
    id TEXT PRIMARY KEY,
    description TEXT,
    status TEXT,
    last_seen TIMESTAMP
)
''')

cursor.execute('''
CREATE TABLE IF NOT EXISTS threads (
    id TEXT PRIMARY KEY,
    name TEXT,
    created_at TIMESTAMP
)
''')

cursor.execute('''
CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    thread_id TEXT,
    sender_id TEXT,
    receiver_id TEXT,
    content TEXT,
    type TEXT,
    timestamp TIMESTAMP,
    FOREIGN KEY (thread_id) REFERENCES threads (id)
)
''')

conn.commit()

# Configuration
CORAL_SERVER_URL = os.environ.get('CORAL_SERVER_URL', 'http://coral.pushcollective.club:5555')
APPLICATION_ID = os.environ.get('APPLICATION_ID', 'exampleApplication')
PRIVACY_KEY = os.environ.get('PRIVACY_KEY', 'privkey')
SESSION_ID = os.environ.get('SESSION_ID', 'b842deb1-95e9-419a-8f57-10bd35ce80d4')  # Using Team Yona's session ID
POLLING_INTERVAL = int(os.environ.get('POLLING_INTERVAL', '5'))  # Polling interval in seconds

# Base URL for API requests
BASE_API_URL = f"{CORAL_SERVER_URL}/devmode/{APPLICATION_ID}/{PRIVACY_KEY}/{SESSION_ID}"
MESSAGE_API_URL = f"{CORAL_SERVER_URL}/devmode/{APPLICATION_ID}/{PRIVACY_KEY}/{SESSION_ID}/message"

def start_monitoring():
    """Start the monitoring process."""
    logger.info(f"Starting passive monitoring of Coral server at {CORAL_SERVER_URL}")
    
    while True:
        try:
            # Fetch agents
            fetch_and_update_agents()
            
            # Fetch threads
            fetch_and_update_threads()
            
            # Fetch messages
            fetch_and_update_messages()
            
            # Sleep for the polling interval
            time.sleep(POLLING_INTERVAL)
        except Exception as e:
            logger.error(f"Error in monitoring loop: {e}")
            time.sleep(POLLING_INTERVAL)

def fetch_and_update_agents():
    """Fetch agents from the Coral server and update the database."""
    try:
        # Try different approaches to get agents
        agents_data = None
        
        # Approach 1: Try direct API request to /agents endpoint
        try:
            response = requests.get(f"{BASE_API_URL}/agents")
            if response.status_code == 200:
                agents_data = response.json()
                logger.info(f"Successfully fetched agents from /agents endpoint")
        except Exception as e:
            logger.warning(f"Failed to fetch agents from /agents endpoint: {e}")
        
        # Approach 2: Try to get agents from message endpoint
        if agents_data is None:
            try:
                # Query parameters to get agents
                params = {
                    "action": "list_agents"
                }
                response = requests.get(MESSAGE_API_URL, params=params)
                if response.status_code == 200:
                    agents_data = response.json()
                    logger.info(f"Successfully fetched agents from message endpoint")
            except Exception as e:
                logger.warning(f"Failed to fetch agents from message endpoint: {e}")
        
        # Approach 3: Try to post to message endpoint
        if agents_data is None:
            try:
                # Post data to get agents
                data = {
                    "action": "list_agents"
                }
                response = requests.post(MESSAGE_API_URL, json=data)
                if response.status_code == 200 or response.status_code == 202:
                    agents_data = response.json()
                    logger.info(f"Successfully fetched agents from message POST endpoint")
            except Exception as e:
                logger.warning(f"Failed to fetch agents from message POST endpoint: {e}")
        
        # If we got agents data, update the database
        if agents_data:
            logger.info(f"Fetched agents: {agents_data}")
            
            # Update the database
            update_agents(agents_data)
            
            # Emit update to clients
            socketio.emit('agents_update', agents_data)
        else:
            logger.warning(f"Failed to fetch agents from all approaches")
            
            # Add a dummy agent for testing
            dummy_agent = {
                "agentId": "dummy-agent",
                "agentDescription": "Dummy agent for testing"
            }
            update_agents([dummy_agent])
            socketio.emit('agents_update', [dummy_agent])
    except Exception as e:
        logger.error(f"Error in fetch_and_update_agents: {e}")

def fetch_and_update_threads():
    """Fetch threads from the Coral server and update the database."""
    try:
        # Try different approaches to get threads
        threads_data = None
        
        # Approach 1: Try direct API request to /threads endpoint
        try:
            response = requests.get(f"{BASE_API_URL}/threads")
            if response.status_code == 200:
                threads_data = response.json()
                logger.info(f"Successfully fetched threads from /threads endpoint")
        except Exception as e:
            logger.warning(f"Failed to fetch threads from /threads endpoint: {e}")
        
        # Approach 2: Try to get threads from message endpoint
        if threads_data is None:
            try:
                # Query parameters to get threads
                params = {
                    "action": "list_threads"
                }
                response = requests.get(MESSAGE_API_URL, params=params)
                if response.status_code == 200:
                    threads_data = response.json()
                    logger.info(f"Successfully fetched threads from message endpoint")
            except Exception as e:
                logger.warning(f"Failed to fetch threads from message endpoint: {e}")
        
        # Approach 3: Try to post to message endpoint
        if threads_data is None:
            try:
                # Post data to get threads
                data = {
                    "action": "list_threads"
                }
                response = requests.post(MESSAGE_API_URL, json=data)
                if response.status_code == 200 or response.status_code == 202:
                    threads_data = response.json()
                    logger.info(f"Successfully fetched threads from message POST endpoint")
            except Exception as e:
                logger.warning(f"Failed to fetch threads from message POST endpoint: {e}")
        
        # If we got threads data, update the database
        if threads_data:
            logger.info(f"Fetched threads: {threads_data}")
            
            # Update the database
            update_threads(threads_data)
            
            # Emit update to clients
            socketio.emit('threads_update', threads_data)
        else:
            logger.warning(f"Failed to fetch threads from all approaches")
            
            # Add a dummy thread for testing
            dummy_thread = {
                "threadId": "dummy-thread",
                "name": "Dummy Thread"
            }
            update_threads([dummy_thread])
            socketio.emit('threads_update', [dummy_thread])
    except Exception as e:
        logger.error(f"Error in fetch_and_update_threads: {e}")

def fetch_and_update_messages():
    """Fetch messages from the Coral server and update the database."""
    try:
        # Try different approaches to get messages
        messages_data = None
        
        # Approach 1: Try direct API request to /messages endpoint
        try:
            response = requests.get(f"{BASE_API_URL}/messages")
            if response.status_code == 200:
                messages_data = response.json()
                logger.info(f"Successfully fetched messages from /messages endpoint")
        except Exception as e:
            logger.warning(f"Failed to fetch messages from /messages endpoint: {e}")
        
        # Approach 2: Try to get messages from message endpoint
        if messages_data is None:
            try:
                # Query parameters to get messages
                params = {
                    "action": "list_messages"
                }
                response = requests.get(MESSAGE_API_URL, params=params)
                if response.status_code == 200:
                    messages_data = response.json()
                    logger.info(f"Successfully fetched messages from message endpoint")
            except Exception as e:
                logger.warning(f"Failed to fetch messages from message endpoint: {e}")
        
        # Approach 3: Try to post to message endpoint
        if messages_data is None:
            try:
                # Post data to get messages
                data = {
                    "action": "list_messages"
                }
                response = requests.post(MESSAGE_API_URL, json=data)
                if response.status_code == 200 or response.status_code == 202:
                    messages_data = response.json()
                    logger.info(f"Successfully fetched messages from message POST endpoint")
            except Exception as e:
                logger.warning(f"Failed to fetch messages from message POST endpoint: {e}")
        
        # If we got messages data, update the database
        if messages_data:
            logger.info(f"Fetched messages: {messages_data}")
            
            # Process and store messages
            process_messages(messages_data)
            
            # Emit update to clients
            socketio.emit('messages_update', messages_data)
        else:
            logger.warning(f"Failed to fetch messages from all approaches")
            
            # Add a dummy message for testing
            dummy_message = {
                "threadId": "dummy-thread",
                "content": "This is a dummy message for testing",
                "senderId": "dummy-agent",
                "mentions": ["dummy-agent-2"]
            }
            process_messages([dummy_message])
            socketio.emit('messages_update', [dummy_message])
    except Exception as e:
        logger.error(f"Error in fetch_and_update_messages: {e}")

def update_agents(agents):
    """Update agent information in the database."""
    try:
        # Log the agents data for debugging
        logger.info(f"Processing agents data: {agents}")
        
        # Handle different types of agent data
        if isinstance(agents, list):
            agent_list = agents
        elif isinstance(agents, dict):
            agent_list = [agents]
        elif isinstance(agents, str):
            try:
                # Try to parse as JSON
                parsed_data = json.loads(agents)
                if isinstance(parsed_data, list):
                    agent_list = parsed_data
                elif isinstance(parsed_data, dict):
                    agent_list = [parsed_data]
                else:
                    logger.warning(f"Unexpected JSON format for agents: {parsed_data}")
                    agent_list = []
            except json.JSONDecodeError:
                # If it's not JSON, it might be a single agent ID
                agent_list = [{"agentId": agents, "agentDescription": "Unknown agent"}]
        else:
            logger.warning(f"Unexpected type for agents: {type(agents)}")
            agent_list = []
        
        for agent in agent_list:
            if isinstance(agent, dict):
                agent_id = agent.get("agentId")
                description = agent.get("agentDescription")
            elif isinstance(agent, str):
                try:
                    agent_data = json.loads(agent)
                    agent_id = agent_data.get("agentId")
                    description = agent_data.get("agentDescription")
                except:
                    # If it's not JSON, it might be a single agent ID
                    agent_id = agent
                    description = "Unknown agent"
            else:
                continue
            
            if not agent_id:
                continue
            
            logger.info(f"Adding agent to database: {agent_id}, {description}")
            cursor.execute(
                "INSERT OR REPLACE INTO agents (id, description, status, last_seen) VALUES (?, ?, ?, datetime('now'))",
                (agent_id, description, "active")
            )
        
        conn.commit()
    except Exception as e:
        logger.error(f"Error updating agents: {e}")

def update_threads(threads):
    """Update thread information in the database."""
    try:
        # Log the threads data for debugging
        logger.info(f"Processing threads data: {threads}")
        
        # Handle different types of thread data
        if isinstance(threads, list):
            thread_list = threads
        elif isinstance(threads, dict):
            thread_list = [threads]
        elif isinstance(threads, str):
            try:
                # Try to parse as JSON
                parsed_data = json.loads(threads)
                if isinstance(parsed_data, list):
                    thread_list = parsed_data
                elif isinstance(parsed_data, dict):
                    thread_list = [parsed_data]
                else:
                    logger.warning(f"Unexpected JSON format for threads: {parsed_data}")
                    thread_list = []
            except json.JSONDecodeError:
                # If it's not JSON, it might be a single thread ID
                thread_list = [{"threadId": threads, "name": f"Thread {threads}"}]
        else:
            logger.warning(f"Unexpected type for threads: {type(threads)}")
            thread_list = []
        
        for thread in thread_list:
            if isinstance(thread, dict):
                thread_id = thread.get("threadId")
                name = thread.get("name", f"Thread {thread_id}")
            elif isinstance(thread, str):
                try:
                    thread_data = json.loads(thread)
                    thread_id = thread_data.get("threadId")
                    name = thread_data.get("name", f"Thread {thread_id}")
                except:
                    # If it's not JSON, it might be a single thread ID
                    thread_id = thread
                    name = f"Thread {thread_id}"
            else:
                continue
            
            if not thread_id:
                continue
            
            logger.info(f"Adding thread to database: {thread_id}, {name}")
            cursor.execute(
                "INSERT OR IGNORE INTO threads (id, name, created_at) VALUES (?, ?, datetime('now'))",
                (thread_id, name)
            )
        
        conn.commit()
    except Exception as e:
        logger.error(f"Error updating threads: {e}")

def process_messages(messages):
    """Process messages and store them in the database."""
    try:
        # Log the messages data for debugging
        logger.info(f"Processing messages data: {messages}")
        
        # Handle different types of message data
        if isinstance(messages, list):
            message_list = messages
        elif isinstance(messages, dict):
            message_list = [messages]
        elif isinstance(messages, str):
            try:
                # Try to parse as JSON
                parsed_data = json.loads(messages)
                if isinstance(parsed_data, list):
                    message_list = parsed_data
                elif isinstance(parsed_data, dict):
                    message_list = [parsed_data]
                else:
                    logger.warning(f"Unexpected JSON format for messages: {parsed_data}")
                    message_list = []
            except json.JSONDecodeError:
                # If it's not JSON, it might be a single message
                message_list = [{"content": messages}]
        else:
            logger.warning(f"Unexpected type for messages: {type(messages)}")
            message_list = []
        
        for message in message_list:
            # Extract data from message
            thread_id = None
            content = None
            sender_id = None
            receiver_id = None
            
            if isinstance(message, dict):
                thread_id = message.get("threadId")
                content = message.get("content")
                sender_id = message.get("senderId")
                receiver_ids = message.get("mentions", [])
                receiver_id = receiver_ids[0] if receiver_ids else None
            elif isinstance(message, str):
                # Try to parse as JSON
                try:
                    data = json.loads(message)
                    thread_id = data.get("threadId")
                    content = data.get("content")
                    sender_id = data.get("senderId")
                    receiver_ids = data.get("mentions", [])
                    receiver_id = receiver_ids[0] if receiver_ids else None
                except:
                    # Use default values
                    thread_id = "unknown"
                    content = message
                    sender_id = "unknown"
                    receiver_id = None
            
            # Skip if no thread ID
            if not thread_id:
                continue
            
            # Determine message type
            message_type = "unknown"
            try:
                if isinstance(content, str):
                    content_data = json.loads(content)
                    message_type = content_data.get("type", "unknown")
                elif isinstance(content, dict):
                    message_type = content.get("type", "unknown")
            except:
                pass
            
            # Store in database
            message_id = str(uuid.uuid4())
            cursor.execute(
                """
                INSERT INTO messages 
                (id, thread_id, sender_id, receiver_id, content, type, timestamp) 
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
                """,
                (message_id, thread_id, sender_id, receiver_id, 
                 json.dumps(content) if isinstance(content, (dict, list)) else content, 
                 message_type)
            )
            
            # Update thread information
            cursor.execute(
                "INSERT OR IGNORE INTO threads (id, name, created_at) VALUES (?, ?, datetime('now'))",
                (thread_id, f"Thread {thread_id}")
            )
            
            conn.commit()
            
            # Emit message update
            socketio.emit('message_update', {
                "id": message_id,
                "thread_id": thread_id,
                "sender_id": sender_id,
                "receiver_id": receiver_id,
                "content": content,
                "type": message_type,
                "timestamp": datetime.now().isoformat()
            })
    except Exception as e:
        logger.error(f"Error processing messages: {e}")

# API endpoints
@app.route('/')
def index():
    """Serve the main HTML page."""
    return send_from_directory(app.static_folder, 'index.html')

@app.route('/api/agents', methods=['GET'])
def get_agents():
    """Get all agents."""
    try:
        cursor.execute("SELECT * FROM agents ORDER BY last_seen DESC")
        agents = [
            {
                "id": row[0], 
                "description": row[1], 
                "status": row[2], 
                "last_seen": row[3]
            } 
            for row in cursor.fetchall()
        ]
        return jsonify(agents)
    except Exception as e:
        logger.error(f"Error getting agents: {e}")
        return jsonify([])

@app.route('/api/threads', methods=['GET'])
def get_threads():
    """Get all threads."""
    try:
        cursor.execute("SELECT * FROM threads ORDER BY created_at DESC")
        threads = [
            {
                "id": row[0], 
                "name": row[1], 
                "created_at": row[2]
            } 
            for row in cursor.fetchall()
        ]
        return jsonify(threads)
    except Exception as e:
        logger.error(f"Error getting threads: {e}")
        return jsonify([])

@app.route('/api/messages', methods=['GET'])
def get_messages():
    """Get messages, optionally filtered by thread ID."""
    try:
        thread_id = request.args.get('thread_id')
        
        if thread_id:
            cursor.execute(
                """
                SELECT id, thread_id, sender_id, receiver_id, content, type, timestamp 
                FROM messages 
                WHERE thread_id = ? 
                ORDER BY timestamp
                """, 
                (thread_id,)
            )
        else:
            cursor.execute(
                """
                SELECT id, thread_id, sender_id, receiver_id, content, type, timestamp 
                FROM messages 
                ORDER BY timestamp DESC 
                LIMIT 100
                """
            )
        
        messages = [
            {
                "id": row[0], 
                "thread_id": row[1], 
                "sender_id": row[2], 
                "receiver_id": row[3], 
                "content": row[4], 
                "type": row[5], 
                "timestamp": row[6]
            } 
            for row in cursor.fetchall()
        ]
        return jsonify(messages)
    except Exception as e:
        logger.error(f"Error getting messages: {e}")
        return jsonify([])

@app.route('/api/stats', methods=['GET'])
def get_stats():
    """Get statistics about the system."""
    try:
        # Get agent count
        cursor.execute("SELECT COUNT(*) FROM agents")
        agent_count = cursor.fetchone()[0]
        
        # Get thread count
        cursor.execute("SELECT COUNT(*) FROM threads")
        thread_count = cursor.fetchone()[0]
        
        # Get message count
        cursor.execute("SELECT COUNT(*) FROM messages")
        message_count = cursor.fetchone()[0]
        
        # Get message types
        cursor.execute("SELECT type, COUNT(*) FROM messages GROUP BY type")
        message_types = {row[0]: row[1] for row in cursor.fetchall()}
        
        # Get recent activity
        cursor.execute(
            """
            SELECT COUNT(*) 
            FROM messages 
            WHERE timestamp > datetime('now', '-1 hour')
            """
        )
        recent_activity = cursor.fetchone()[0]
        
        return jsonify({
            "agent_count": agent_count,
            "thread_count": thread_count,
            "message_count": message_count,
            "message_types": message_types,
            "recent_activity": recent_activity
        })
    except Exception as e:
        logger.error(f"Error getting stats: {e}")
        return jsonify({})

# Add a direct API check endpoint
@app.route('/api/check-coral-server', methods=['GET'])
def check_coral_server():
    """Check if the Coral server is accessible."""
    results = []
    
    # Check base URL
    try:
        response = requests.get(CORAL_SERVER_URL)
        results.append({
            "endpoint": CORAL_SERVER_URL,
            "status": "success" if response.status_code == 200 else "error",
            "status_code": response.status_code,
            "message": response.text if len(response.text) < 100 else response.text[:100] + "..."
        })
    except Exception as e:
        results.append({
            "endpoint": CORAL_SERVER_URL,
            "status": "error",
            "message": str(e)
        })
    
    # Check message endpoint
    try:
        response = requests.get(MESSAGE_API_URL)
        results.append({
            "endpoint": MESSAGE_API_URL,
            "status": "success" if response.status_code == 200 else "error",
            "status_code": response.status_code,
            "message": response.text if len(response.text) < 100 else response.text[:100] + "..."
        })
    except Exception as e:
        results.append({
            "endpoint": MESSAGE_API_URL,
            "status": "error",
            "message": str(e)
        })
    
    # Check agents endpoint
    try:
        response = requests.get(f"{BASE_API_URL}/agents")
        results.append({
            "endpoint": f"{BASE_API_URL}/agents",
            "status": "success" if response.status_code == 200 else "error",
            "status_code": response.status_code,
            "message": response.text if len(response.text) < 100 else response.text[:100] + "..."
        })
    except Exception as e:
        results.append({
            "endpoint": f"{BASE_API_URL}/agents",
            "status": "error",
            "message": str(e)
        })
    
    return jsonify({
        "results": results,
        "timestamp": datetime.now().isoformat()
    })

# Start the monitoring in a separate thread
def start_monitoring_thread():
    """Start the monitoring in a separate thread."""
    start_monitoring()

# Start the monitoring thread
monitoring_thread = threading.Thread(target=start_monitoring_thread, daemon=True)
monitoring_thread.start()

# Run the Flask app
if __name__ == '__main__':
    port = int(os.environ.get('PORT', 8080))
    socketio.run(app, host='0.0.0.0', port=port, allow_unsafe_werkzeug=True)
