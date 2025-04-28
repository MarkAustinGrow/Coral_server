@echo off
echo Building and running the Coral server Docker container...
docker-compose up -d --build

echo Waiting for the container to start...
timeout /t 5 /nobreak > nul

echo Checking if the container is running...
docker ps | findstr coral-server

echo Displaying the logs...
docker logs coral-server

echo.
echo The Coral server should now be running at http://localhost:3001
echo Press Ctrl+C to stop viewing the logs, but the server will continue running in the background.
echo To stop the server, run: docker-compose down
echo.

echo Following logs (press Ctrl+C to exit)...
docker logs -f coral-server
