# Coral Protocol Monitor

A web-based monitoring tool for visualizing agent communications using the Coral Protocol.

## Overview

The Coral Protocol Monitor provides a real-time dashboard for monitoring agent communications in a Coral Protocol environment. It allows you to:

- View connected agents and their status
- Monitor communication threads
- Inspect message content and flow
- Track statistics about agent interactions
- Visualize communication patterns

This tool is particularly useful for debugging multi-agent systems, demonstrating agent interactions, and monitoring the health of your Coral Protocol deployment.

## Features

- **Real-time Updates**: See agent communications as they happen
- **Agent Tracking**: Monitor which agents are connected and active
- **Thread Management**: View and filter communication threads
- **Message Inspection**: Examine the content of messages between agents
- **Statistics**: Track metrics like message volume and types
- **Visualizations**: See communication patterns through charts and graphs

## Installation

### Prerequisites

- Python 3.8 or higher
- Access to a Coral Protocol server
- pip (Python package manager)

### Setup

1. Clone this repository or download the files:

```bash
git clone https://github.com/your-username/coral-protocol-monitor.git
cd coral-protocol-monitor
```

2. Install the required dependencies:

```bash
pip install -r coral_monitor_requirements.txt
```

3. Configure the environment variables (optional):

```bash
# Set the Coral server URL (default: http://coral.pushcollective.club:5555)
export CORAL_SERVER_URL=http://your-coral-server:5555

# Set the application ID (default: exampleApplication)
export APPLICATION_ID=your-application-id

# Set the privacy key (default: privkey)
export PRIVACY_KEY=your-privacy-key

# Set the session ID (default: session1)
export SESSION_ID=your-session-id

# Set the monitor agent ID (default: monitor_agent)
export MONITOR_AGENT_ID=your-monitor-agent-id

# Set the port for the web interface (default: 8080)
export PORT=8080
```

## Usage

1. Start the monitoring service:

```bash
python coral_monitor_service.py
```

2. Open your web browser and navigate to:

```
http://localhost:8080
```

3. The dashboard will automatically connect to the Coral server and start monitoring agent communications.

## Dashboard Sections

### Statistics

The statistics section shows:

- Number of connected agents
- Number of active threads
- Total message count
- Recent activity (messages in the last hour)
- Distribution of message types

### Agents

The agents section lists all connected agents with:

- Agent ID
- Description
- Status (active/inactive)
- Last seen timestamp

### Threads

The threads section shows all communication threads with:

- Thread name
- Thread ID
- Creation timestamp

Click on a thread to view its messages.

### Communication Flow

The communication flow chart shows the volume of messages over time, helping you visualize communication patterns.

### Messages

The messages section displays:

- Sender and receiver
- Message type
- Timestamp
- Content preview

Click on a message to view its full details.

## Troubleshooting

### Connection Issues

If the monitor can't connect to the Coral server:

1. Check that the Coral server is running
2. Verify the server URL, application ID, and privacy key
3. Ensure the firewall allows connections to the server
4. Check the console logs for error messages

### No Agents Showing

If no agents are displayed:

1. Verify that agents are connected to the same session
2. Check that agents are using the correct application ID and privacy key
3. Restart the monitoring service

### Database Issues

If you encounter database errors:

1. Delete the `coral_monitor.db` file to reset the database
2. Restart the monitoring service

## Advanced Configuration

### Custom Database Path

You can change the database path by modifying the `DB_PATH` variable in `coral_monitor_service.py`.

### SSL Support

To enable SSL:

1. Generate SSL certificates
2. Modify the `socketio.run()` call in `coral_monitor_service.py` to include SSL parameters:

```python
socketio.run(app, host='0.0.0.0', port=port, ssl_context=('cert.pem', 'key.pem'))
```

### Custom Styling

To customize the appearance:

1. Modify the `web/styles.css` file
2. Restart the monitoring service

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
