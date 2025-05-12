#!/bin/bash

# Script to deploy LangChain compatibility changes to the Coral server on Linode
# Usage: ./deploy-to-linode.sh [hostname]

# Default hostname
HOSTNAME=${1:-"coral.pushcollective.club"}

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Deploying LangChain compatibility changes to Coral server on ${HOSTNAME}${NC}"

# Create temporary directory for files
echo -e "${YELLOW}Creating temporary directory...${NC}"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

# Copy files to temporary directory
echo -e "${YELLOW}Copying files to temporary directory...${NC}"
cp src/main/resources/application.yaml "$TEMP_DIR/application.yaml"
cp src/main/kotlin/org/coralprotocol/coralserver/Main.kt "$TEMP_DIR/Main.kt"

# Create deployment script
echo -e "${YELLOW}Creating deployment script...${NC}"
cat > "$TEMP_DIR/deploy.sh" << 'EOF'
#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# Navigate to Coral server directory
echo -e "${YELLOW}Navigating to Coral server directory...${NC}"
cd /opt/coral-server || { echo -e "${RED}Failed to navigate to /opt/coral-server${NC}"; exit 1; }

# Backup current files
echo -e "${YELLOW}Backing up current files...${NC}"
cp src/main/resources/application.yaml src/main/resources/application.yaml.bak
cp src/main/kotlin/org/coralprotocol/coralserver/Main.kt src/main/kotlin/org/coralprotocol/coralserver/Main.kt.bak

# Update files
echo -e "${YELLOW}Updating application.yaml...${NC}"
cp /tmp/application.yaml src/main/resources/application.yaml
echo -e "${YELLOW}Updating Main.kt...${NC}"
cp /tmp/Main.kt src/main/kotlin/org/coralprotocol/coralserver/Main.kt

# Build the project
echo -e "${YELLOW}Building the project...${NC}"
./gradlew build || { echo -e "${RED}Build failed${NC}"; exit 1; }

# Check if the server is running as a systemd service
if systemctl is-active --quiet coral-server; then
    # Update the systemd service file
    echo -e "${YELLOW}Updating systemd service file...${NC}"
    mkdir -p /etc/systemd/system/coral-server.service.d/
    cat > /etc/systemd/system/coral-server.service.d/override.conf << 'EOL'
[Service]
ExecStart=
ExecStart=/usr/bin/java -jar /opt/coral-server/build/libs/coral-server-1.0-SNAPSHOT.jar --sse-server 5555
EOL

    # Reload systemd and restart the service
    echo -e "${YELLOW}Reloading systemd and restarting the service...${NC}"
    systemctl daemon-reload
    systemctl restart coral-server

    # Check if the service started successfully
    if systemctl is-active --quiet coral-server; then
        echo -e "${GREEN}Coral server service restarted successfully${NC}"
    else
        echo -e "${RED}Failed to restart Coral server service${NC}"
        echo -e "${YELLOW}Checking logs...${NC}"
        journalctl -u coral-server -n 20
        exit 1
    fi
else
    echo -e "${YELLOW}Coral server is not running as a systemd service${NC}"
    echo -e "${YELLOW}Starting the server manually...${NC}"
    nohup java -jar build/libs/coral-server-1.0-SNAPSHOT.jar --sse-server 5555 > coral-server.log 2>&1 &
    echo -e "${GREEN}Server started with PID $!${NC}"
fi

# Verify the server is running on the new port
echo -e "${YELLOW}Verifying the server is running on port 5555...${NC}"
sleep 5
if netstat -tuln | grep -q ":5555 "; then
    echo -e "${GREEN}Server is running on port 5555${NC}"
else
    echo -e "${RED}Server is not running on port 5555${NC}"
    echo -e "${YELLOW}Checking logs...${NC}"
    if [ -f coral-server.log ]; then
        tail -n 20 coral-server.log
    else
        journalctl -u coral-server -n 20
    fi
    exit 1
fi

echo -e "${GREEN}Deployment completed successfully${NC}"
EOF

# Make the deployment script executable
chmod +x "$TEMP_DIR/deploy.sh"

# Copy files to the server
echo -e "${YELLOW}Copying files to the server...${NC}"
scp "$TEMP_DIR/application.yaml" "$TEMP_DIR/Main.kt" "$TEMP_DIR/deploy.sh" "root@$HOSTNAME:/tmp/" || { echo -e "${RED}Failed to copy files to the server${NC}"; exit 1; }

# Execute the deployment script on the server
echo -e "${YELLOW}Executing deployment script on the server...${NC}"
ssh "root@$HOSTNAME" "bash /tmp/deploy.sh" || { echo -e "${RED}Deployment failed${NC}"; exit 1; }

echo -e "${GREEN}Deployment completed successfully${NC}"
