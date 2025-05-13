#!/bin/bash

# Script to update the Coral Protocol Monitor on the Linode server
# Usage: ./update-monitor-on-linode.sh [server-ip] [username]

# Default values
SERVER_IP=${1:-"coral.pushcollective.club"}
USERNAME=${2:-"root"}

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Updating Coral Protocol Monitor on Linode server${NC}"

# Check if required files exist
if [ ! -f "coral_monitor_service.py" ] || [ ! -f "coral-monitor.service" ]; then
    echo -e "${RED}Required files not found. Make sure you're in the correct directory.${NC}"
    exit 1
fi

# Copy the updated files to the server
echo -e "${YELLOW}Copying updated files to server...${NC}"
scp coral_monitor_service.py ${USERNAME}@${SERVER_IP}:/root/Coral_server/
scp coral-monitor.service ${USERNAME}@${SERVER_IP}:/etc/systemd/system/

# Execute commands on the server
echo -e "${YELLOW}Updating the monitor on the server...${NC}"
ssh ${USERNAME}@${SERVER_IP} << EOF
# Reload systemd
systemctl daemon-reload

# Restart the service
systemctl restart coral-monitor.service

# Check the status
systemctl status coral-monitor.service

# Show the logs
journalctl -u coral-monitor.service -n 20
EOF

echo -e "${GREEN}Update complete!${NC}"
echo -e "${GREEN}You can access the monitor at: http://${SERVER_IP}:8080${NC}"
