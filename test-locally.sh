#!/bin/bash

# Build and run the Docker container locally
echo "Building and running the Coral server Docker container..."
docker-compose up -d --build

# Wait for the container to start
echo "Waiting for the container to start..."
sleep 5

# Check if the container is running
echo "Checking if the container is running..."
docker ps | grep coral-server

# Display the logs
echo "Displaying the logs..."
docker logs coral-server

echo ""
echo "The Coral server should now be running at http://localhost:3001"
echo "Press Ctrl+C to stop viewing the logs, but the server will continue running in the background."
echo "To stop the server, run: docker-compose down"

# Follow the logs
docker logs -f coral-server
