import asyncio
import json
import logging
import os
import sqlite3
import threading
import uuid
from datetime import datetime
from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS
from flask_socketio import SocketIO
from langchain_mcp_adapters.client import MultiServerMCPClient
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
SESSION_ID = os.environ.get('SESSION_ID', 'session1')
MONITOR_AGENT_ID = os.environ.get('MONITOR_AGENT_ID', 'monitor_agent')

# Coral server connection
async def connect_to_coral():
    """Connect to the Coral server and start monitoring."""
    try:
        base_url = f"{CORAL_SERVER_URL}/devmode/{APPLICATION_ID}/{PRIVACY_KEY}/{SESSION_ID}/sse"
        params = {
            "waitForAgents": 1,
            "agentId": MONITOR_AGENT_ID,
            "agentDescription": "Monitoring agent for web interface"
        }
        query_string = urllib.parse.urlencode(params)
        mcp_server_url = f"{base_url}?{query_string}"
        
        logger.info(f"Connecting to Coral server at {mcp_server_url}")
        
        client = MultiServerMCPClient(
            connections={
                "coral": {
                    "transport": "sse",
                    "url": mcp_server_url,
                    "timeout": 300,
                    "sse_read_timeout": 300,
                }
            }
        )
        
        await client.__aenter__()
        logger.info("Connected to Coral server")
        
        # Start monitoring
        await monitor_communications(client)
    except Exception as e:
        logger.error(f"Error connecting to Coral server: {e}")
        await asyncio.sleep(10)
        await connect_to_coral()

async def monitor_communications(client):
    """Monitor communications between agents."""
    while True:
        try:
            # Get tools from the client
            tools = client.get_tools()
            
            # Find the list_agents tool
            list_agents_tool = next((t for t in tools if t.name == "list_agents"), None)
            if list_agents_tool:
                agents = await list_agents_tool.ainvoke({})
                update_agents(agents)
                socketio.emit('agents_update', agents)
            
            # List threads
            try:
                threads = await list_all_threads(client)
                update_threads(threads)
                socketio.emit('threads_update', threads)
            except Exception as e:
                logger.error(f"Error listing threads: {e}")
            
            # Find the wait_for_mentions tool
            wait_for_mentions_tool = next((t for t in tools if t.name == "wait_for_mentions"), None)
            if wait_for_mentions_tool:
                mentions = await wait_for_mentions_tool.ainvoke({"timeoutMs": 5000})
                
                if mentions:
                    process_mentions(mentions)
                    socketio.emit('mentions_update', mentions)
            
            await asyncio.sleep(1)
        except Exception as e:
            logger.error(f"Error in monitoring: {e}")
            await asyncio.sleep(5)

async def list_all_threads(client):
    """List all threads by creating a temporary thread and checking for existing ones."""
    try:
        # Get tools from the client
        tools = client.get_tools()
        
        # Find the create_thread tool
        create_thread_tool = next((t for t in tools if t.name == "create_thread"), None)
        if create_thread_tool:
            temp_thread = await create_thread_tool.ainvoke({
                "name": f"Monitor Thread {uuid.uuid4()}",
                "participants": [MONITOR_AGENT_ID]
            })
            
            # Get thread ID
            thread_id = None
            if isinstance(temp_thread, dict):
                thread_id = temp_thread.get("threadId")
            elif isinstance(temp_thread, str):
                try:
                    thread_data = json.loads(temp_thread)
                    thread_id = thread_data.get("threadId")
                except:
                    thread_id = temp_thread
            
            if not thread_id:
                logger.warning("Failed to create temporary thread")
                return []
            
            # Try to list threads
            list_threads_tool = next((t for t in tools if t.name == "list_threads"), None)
            if list_threads_tool:
                try:
                    threads = await list_threads_tool.ainvoke({})
                    return threads
                except:
                    # If list_threads doesn't exist, return the temporary thread
                    return [{"threadId": thread_id, "name": f"Monitor Thread"}]
            else:
                return [{"threadId": thread_id, "name": f"Monitor Thread"}]
        else:
            logger.warning("Create thread tool not found")
            return []
    except Exception as e:
        logger.error(f"Error listing threads: {e}")
        return []

def update_agents(agents):
    """Update agent information in the database."""
    try:
        for agent in agents:
            if isinstance(agent, dict):
                agent_id = agent.get("agentId")
                description = agent.get("agentDescription")
            elif isinstance(agent, str):
                try:
                    agent_data = json.loads(agent)
                    agent_id = agent_data.get("agentId")
                    description = agent_data.get("agentDescription")
                except:
                    continue
            else:
                continue
            
            if not agent_id:
                continue
            
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
        for thread in threads:
            if isinstance(thread, dict):
                thread_id = thread.get("threadId")
                name = thread.get("name", f"Thread {thread_id}")
            elif isinstance(thread, str):
                try:
                    thread_data = json.loads(thread)
                    thread_id = thread_data.get("threadId")
                    name = thread_data.get("name", f"Thread {thread_id}")
                except:
                    continue
            else:
                continue
            
            if not thread_id:
                continue
            
            cursor.execute(
                "INSERT OR IGNORE INTO threads (id, name, created_at) VALUES (?, ?, datetime('now'))",
                (thread_id, name)
            )
        
        conn.commit()
    except Exception as e:
        logger.error(f"Error updating threads: {e}")

def process_mentions(mentions):
    """Process mentions and store them in the database."""
    try:
        for mention in mentions:
            # Extract data from mention
            thread_id = None
            content = None
            sender_id = None
            receiver_id = None
            
            if isinstance(mention, dict):
                thread_id = mention.get("threadId")
                content = mention.get("content")
                sender_id = mention.get("senderId")
                receiver_ids = mention.get("mentions", [])
                receiver_id = receiver_ids[0] if receiver_ids else None
            elif isinstance(mention, str):
                # Try to parse as JSON
                try:
                    data = json.loads(mention)
                    thread_id = data.get("threadId")
                    content = data.get("content")
                    sender_id = data.get("senderId")
                    receiver_ids = data.get("mentions", [])
                    receiver_id = receiver_ids[0] if receiver_ids else None
                except:
                    # Use default values
                    thread_id = "unknown"
                    content = mention
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
        logger.error(f"Error processing mentions: {e}")

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

# Start the Coral connection in a separate thread
def start_coral_connection():
    """Start the connection to the Coral server in a separate thread."""
    asyncio.run(connect_to_coral())

# Start the monitoring thread
monitoring_thread = threading.Thread(target=start_coral_connection, daemon=True)
monitoring_thread.start()

# Run the Flask app
if __name__ == '__main__':
    port = int(os.environ.get('PORT', 8080))
    socketio.run(app, host='0.0.0.0', port=port)
