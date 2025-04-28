#!/bin/bash
# deploy-stability-improvements.sh
#
# This script deploys the connection stability improvements to the Coral server.
# It copies the updated files, rebuilds the server, and restarts the Docker container.

set -e  # Exit on error

echo "=== Coral Server Connection Stability Deployment ==="
echo "This script will deploy the connection stability improvements to your Coral server."
echo ""

# Check if we're on the Linode server
if [ ! -d "/root/Coral_server" ]; then
  echo "Error: This script should be run on the Linode server where Coral server is installed."
  echo "Please copy this script to your Linode server and run it there."
  exit 1
fi

# Backup the original files
echo "Creating backups of original files..."
BACKUP_DIR="/root/Coral_server/backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR

cp /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/server/SseServer.kt $BACKUP_DIR/
cp /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/SseRoutes.kt $BACKUP_DIR/
cp /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/MessageRoutes.kt $BACKUP_DIR/

echo "Backed up original files to $BACKUP_DIR"

# Copy the updated files
echo "Copying updated files..."
cp SseServer.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/server/
cp SseRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/
cp MessageRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/

# Rebuild the server
echo "Rebuilding the server..."
cd /root/Coral_server/coral-server-master
./gradlew build

# Check if the build was successful
if [ $? -ne 0 ]; then
  echo "Error: Build failed. Restoring original files..."
  cp $BACKUP_DIR/SseServer.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/server/
  cp $BACKUP_DIR/SseRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/
  cp $BACKUP_DIR/MessageRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/
  exit 1
fi

# Restart the Docker container
echo "Restarting the Docker container..."
cd /root/Coral_server
docker compose down
docker compose up -d

# Check if the container started successfully
if [ $(docker ps | grep coral-server | wc -l) -eq 0 ]; then
  echo "Error: Docker container failed to start. Restoring original files..."
  cp $BACKUP_DIR/SseServer.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/server/
  cp $BACKUP_DIR/SseRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/
  cp $BACKUP_DIR/MessageRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/
  
  echo "Rebuilding with original files..."
  cd /root/Coral_server/coral-server-master
  ./gradlew build
  
  echo "Restarting Docker container with original files..."
  cd /root/Coral_server
  docker compose up -d
  
  exit 1
fi

# Show the logs
echo "Deployment successful! Showing logs..."
echo "Press Ctrl+C to exit the logs."
sleep 2
docker logs -f coral_server-coral-server-1
