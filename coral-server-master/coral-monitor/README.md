# Coral Monitor

A monitoring dashboard for the Coral Server that provides a visual interface to monitor agent communication.

## Features

- View sessions, agents, threads, and messages
- Interactive dashboard with real-time updates
- Filter and explore agent communication patterns
- Monitor thread activity and message flow

## Prerequisites

- Docker and Docker Compose
- Running Coral Server instance on port 3001

## Installation

### Local Development

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the server:
   ```bash
   npm start
   ```

3. Access the dashboard at http://localhost:3002

### Docker Deployment

1. Build and run using Docker Compose:
   ```bash
   docker-compose up -d
   ```

2. Access the dashboard at http://localhost:3002

## Deployment on Linode

1. Copy the coral-monitor directory to your Linode server:
   ```bash
   scp -r coral-monitor root@your-linode-ip:/root/Coral_server/
   ```

2. SSH into your Linode server:
   ```bash
   ssh root@your-linode-ip
   ```

3. Navigate to the coral-monitor directory:
   ```bash
   cd /root/Coral_server/coral-monitor
   ```

4. Build and run the Docker container:
   ```bash
   docker-compose up -d
   ```

5. Access the dashboard at http://your-linode-ip:3002

## Configuration

The monitor is configured to connect to a Coral Server running on localhost:3001. If your Coral Server is running on a different host or port, update the `coralServerUrl` in `server.js`.

## API Endpoints

The monitor proxies requests to the following Coral Server API endpoints:

- `/sessions` - List all sessions
- `/sessions/{sessionId}` - Get session details
- `/sessions/{sessionId}/agents` - List agents in a session
- `/sessions/{sessionId}/threads` - List threads in a session
- `/sessions/{sessionId}/threads/{threadId}/messages` - List messages in a thread

## Troubleshooting

If you encounter issues:

1. Make sure the Coral Server is running and accessible
2. Check the logs of the coral-monitor container:
   ```bash
   docker logs coral-monitor_coral-monitor_1
   ```
3. Verify that the network configuration allows the monitor to access the Coral Server
