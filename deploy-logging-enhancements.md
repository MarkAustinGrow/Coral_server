# Deploying Logging Enhancements to Coral Server

This document provides instructions for deploying the enhanced logging and client registration fixes to your Coral server on Linode.

## What's Included in the Enhancements

1. **Enhanced Logging**
   - Detailed HTTP request logging
   - Routing configuration logs
   - Error handling logs
   - Tool processing logs
   - Agent registration logs

2. **Client Registration Fix**
   - Added missing `register_agent` tool
   - Updated tool registry to include the new tool

3. **Debug Mode Options**
   - Command-line options for debug and trace logging
   - Environment variables in Docker for easy configuration

## Deployment Options

### Option 1: Deploy from the enhance-logging Branch

This option pulls the latest changes from the `enhance-logging` branch and deploys them to your Linode server.

```bash
# SSH into your Linode server
ssh root@172.237.124.61

# Clone the repository if you haven't already
git clone https://github.com/MarkAustinGrow/Coral_server.git
cd Coral_server

# Checkout the enhance-logging branch
git checkout enhance-logging

# Create logs directory
mkdir -p logs

# Build and run the Docker container with debug logging enabled
docker-compose up -d --build
```

### Option 2: Update Existing Deployment

If you already have the repository cloned on your Linode server, you can update it to include the latest changes.

```bash
# SSH into your Linode server
ssh root@172.237.124.61

# Navigate to the repository
cd Coral_server

# Pull the latest changes from the enhance-logging branch
git fetch origin
git checkout enhance-logging
git pull

# Create logs directory
mkdir -p logs

# Rebuild and restart the Docker container
docker-compose up -d --build
```

## Verifying the Deployment

After deploying the changes, you can verify that they're working correctly:

1. Check the logs to see if the enhanced logging is working:
   ```bash
   docker logs coral_server-coral-server-1
   ```

2. Look for log messages related to the server startup, routing configuration, and HTTP requests.

3. Try connecting a client to the server and registering an agent. The logs should show detailed information about the registration process.

## Accessing the Logs

The logs are stored in two places:

1. **Docker Container Logs**
   ```bash
   docker logs coral_server-coral-server-1
   ```

2. **Mounted Logs Directory**
   ```bash
   ls -la logs/
   ```

## Troubleshooting

If you encounter any issues with the deployment:

1. Check the Docker container status:
   ```bash
   docker ps
   ```

2. Check the Docker container logs:
   ```bash
   docker logs coral_server-coral-server-1
   ```

3. Try restarting the container:
   ```bash
   docker-compose down
   docker-compose up -d
   ```

4. If the issues persist, try enabling trace logging by setting `TRACE_MODE=true` in the `docker-compose.yml` file:
   ```yaml
   environment:
     - DEBUG_MODE=true
     - TRACE_MODE=true
   ```

## Merging the Changes

If you're satisfied with the changes and want to merge them into the main branch:

1. Create a pull request on GitHub:
   https://github.com/MarkAustinGrow/Coral_server/pull/new/enhance-logging

2. Review and merge the pull request.

3. Update your deployment to use the main branch:
   ```bash
   git checkout master
   git pull
   docker-compose up -d --build
