#!/bin/bash

# Coral Server Direct Deployment Script for Linode
# This script assumes Docker is already installed on your Linode server
# Run this script directly on your Linode server after installing Docker

# Exit on error
set -e

echo "=== Coral Server Direct Deployment Script ==="
echo "This script will deploy the Coral server on your Linode server."
echo ""

# Clone the repository if it doesn't exist
if [ ! -d "Coral_server" ]; then
  echo "=== Cloning the repository ==="
  git clone https://github.com/MarkAustinGrow/Coral_server.git
  cd Coral_server
else
  echo "=== Repository already exists, updating ==="
  cd Coral_server
  git pull
fi

# Create logs directory
echo "=== Creating logs directory ==="
mkdir -p logs

# Build and run the Docker container
echo "=== Building and running the Docker container ==="
docker compose up -d --build

# Wait for the container to start
echo "=== Waiting for the container to start ==="
sleep 5

# Check if the container is running
echo "=== Checking if the container is running ==="
docker ps | grep coral-server

# Display the logs
echo "=== Displaying the logs ==="
docker logs coral-server

# Get the public IP address
PUBLIC_IP=$(curl -s http://checkip.amazonaws.com)

echo ""
echo "=== Deployment Complete ==="
echo "The Coral server should now be running at http://$PUBLIC_IP:3001"
echo ""
echo "IMPORTANT: Make sure to configure your firewall to allow incoming traffic on port 3001."
echo "You can do this using the Linode Cloud Firewall or by running:"
echo "sudo ufw allow 3001/tcp"
echo ""
echo "To view the logs, run: docker logs -f coral-server"
echo "To stop the server, run: docker compose down"
