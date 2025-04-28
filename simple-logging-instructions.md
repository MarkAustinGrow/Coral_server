# Simple Logging and RegisterAgent Tool Setup Instructions

I've created a script to implement a simpler approach for adding debug logging and the missing `register_agent` tool to your Coral server. This approach avoids the compilation errors we encountered with the enhanced logging changes.

## What This Script Does

1. Switches to the master branch
2. Sets up debug logging using SimpleLogger
3. Adds the missing `register_agent` tool
4. Updates the ThreadToolsRegistry to include the new tool
5. Rebuilds and restarts the Docker container with the new configuration

## How to Run the Script on Your Linode Server

### Option 1: Download and Run the Script Directly

```bash
# SSH into your Linode server
ssh root@172.237.124.61

# Navigate to your Coral server directory
cd Coral_server

# Download the script
curl -O https://raw.githubusercontent.com/MarkAustinGrow/Coral_server/enhance-logging/simple-logging-setup.sh

# Make the script executable
chmod +x simple-logging-setup.sh

# Run the script
./simple-logging-setup.sh
```

### Option 2: Pull from GitHub and Run

```bash
# SSH into your Linode server
ssh root@172.237.124.61

# Navigate to your Coral server directory
cd Coral_server

# Fetch the latest changes from GitHub
git fetch origin

# Checkout the enhance-logging branch
git checkout enhance-logging

# Make the script executable
chmod +x simple-logging-setup.sh

# Run the script
./simple-logging-setup.sh
```

## Verifying the Setup

After running the script, you should see:

1. The Docker container running with debug logging enabled
2. The `register_agent` tool available for client registration
3. More detailed logs in the Docker container logs

You can check the logs with:

```bash
docker logs coral_server-coral-server-1
```

## Troubleshooting

If you encounter any issues:

1. **Script fails to run**: Make sure the script has execute permissions (`chmod +x simple-logging-setup.sh`)

2. **Git errors**: If you have local changes, you might need to stash them first:
   ```bash
   git stash
   ./simple-logging-setup.sh
   git stash pop  # To restore your changes after the script runs
   ```

3. **Docker build fails**: Check the Docker build logs for specific errors:
   ```bash
   docker compose -f docker-compose-debug.yml logs
   ```

4. **Container doesn't start**: Check if there are any port conflicts:
   ```bash
   netstat -tuln | grep 3001
   ```

## Manual Steps (if the script doesn't work)

If you prefer to perform the steps manually, or if the script encounters issues, here are the individual steps:

1. **Switch to the master branch**:
   ```bash
   git checkout master
   git pull origin master
   ```

2. **Create logs directory and logging configuration**:
   ```bash
   mkdir -p logs
   
   # Create simplelogger.properties file with the following content
   echo "org.slf4j.simpleLogger.defaultLogLevel=DEBUG" > logs/simplelogger.properties
   ```

3. **Create a custom Docker Compose file**:
   ```bash
   # Create docker-compose-debug.yml with volumes and environment variables
   ```

4. **Add the RegisterAgentTool.kt file**:
   ```bash
   # Create the file in the tools directory
   ```

5. **Update the ThreadToolsRegistry.kt file**:
   ```bash
   # Update to include the register_agent tool
   ```

6. **Rebuild and restart the Docker container**:
   ```bash
   docker compose down
   docker compose -f docker-compose-debug.yml up -d --build
   ```

Let me know if you need any clarification or encounter any issues during the setup process!
