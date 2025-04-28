#!/bin/bash

# Coral Server Deployment Script for Linode
# This script automates the process of setting up Docker and deploying the Coral server on a Linode server.

# Exit on error
set -e

echo "=== Coral Server Deployment Script for Linode ==="
echo "This script will install Docker and deploy the Coral server on your Linode server."
echo "Make sure you are running this script on your Linode server."
echo ""

# Update system packages
echo "=== Updating system packages ==="
sudo apt update
sudo apt upgrade -y

# Install Docker dependencies
echo "=== Installing Docker dependencies ==="
sudo apt install -y apt-transport-https ca-certificates curl software-properties-common

# Add Docker's official GPG key
echo "=== Adding Docker's official GPG key ==="
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Add Docker repository
echo "=== Adding Docker repository ==="
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
echo "=== Installing Docker ==="
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Add user to the docker group
echo "=== Adding user to the docker group ==="
sudo usermod -aG docker $USER
echo "NOTE: You may need to log out and log back in for the group changes to take effect."

# Build and run the Docker container
echo "=== Building and running the Docker container ==="
docker-compose up -d --build

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
echo "To stop the server, run: docker-compose down"
