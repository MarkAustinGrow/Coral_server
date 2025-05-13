#!/bin/bash

# Script to deploy the Coral Protocol Monitor to the Linode server
# Usage: ./deploy-monitor-to-linode.sh [server-ip] [username]

# Default values
SERVER_IP=${1:-"coral.pushcollective.club"}
USERNAME=${2:-"root"}

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Deploying Coral Protocol Monitor to Linode server${NC}"

# Check if required files exist
if [ ! -f "coral_monitor_service.py" ] || [ ! -d "web" ]; then
    echo -e "${RED}Required files not found. Make sure you're in the correct directory.${NC}"
    exit 1
fi

# Create deployment directory
echo -e "${YELLOW}Creating deployment directory...${NC}"
mkdir -p deploy_monitor

# Copy required files to deployment directory
echo -e "${YELLOW}Copying files to deployment directory...${NC}"
cp coral_monitor_service.py deploy_monitor/
cp coral_monitor_requirements.txt deploy_monitor/
cp -r web deploy_monitor/
cp coral_monitor_README.md deploy_monitor/

# Create a systemd service file
echo -e "${YELLOW}Creating systemd service file...${NC}"
cat > deploy_monitor/coral-monitor.service << EOF
[Unit]
Description=Coral Protocol Monitor
After=network.target

[Service]
User=root
WorkingDirectory=/root/coral-monitor
ExecStart=/root/coral-monitor/coral_monitor_env/bin/python coral_monitor_service.py
Restart=always
RestartSec=10
Environment=PORT=8080
Environment=CORAL_SERVER_URL=http://localhost:5555

[Install]
WantedBy=multi-user.target
EOF

# Create setup script
echo -e "${YELLOW}Creating setup script...${NC}"
cat > deploy_monitor/setup-monitor.sh << EOF
#!/bin/bash

# Setup script for Coral Protocol Monitor
echo "Setting up Coral Protocol Monitor..."

# Create directory
mkdir -p /root/coral-monitor

# Copy files
cp -r * /root/coral-monitor/

# Create virtual environment
cd /root/coral-monitor
python3 -m venv coral_monitor_env

# Activate virtual environment and install dependencies
source coral_monitor_env/bin/activate
pip install -r coral_monitor_requirements.txt

# Install systemd service
cp coral-monitor.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable coral-monitor.service
systemctl start coral-monitor.service

echo "Coral Protocol Monitor setup complete!"
echo "You can access the monitor at: http://\$(hostname -I | awk '{print \$1}'):8080"
EOF

# Make setup script executable
chmod +x deploy_monitor/setup-monitor.sh

# Create a tar archive
echo -e "${YELLOW}Creating tar archive...${NC}"
tar -czf coral-monitor.tar.gz -C deploy_monitor .

# Copy the archive to the server
echo -e "${YELLOW}Copying archive to server...${NC}"
scp coral-monitor.tar.gz ${USERNAME}@${SERVER_IP}:/root/

# Execute setup script on the server
echo -e "${YELLOW}Setting up monitor on server...${NC}"
ssh ${USERNAME}@${SERVER_IP} << EOF
mkdir -p coral-monitor-setup
tar -xzf coral-monitor.tar.gz -C coral-monitor-setup
cd coral-monitor-setup
bash setup-monitor.sh
EOF

# Clean up
echo -e "${YELLOW}Cleaning up...${NC}"
rm -rf deploy_monitor
rm coral-monitor.tar.gz

echo -e "${GREEN}Deployment complete!${NC}"
echo -e "${GREEN}You can access the monitor at: http://${SERVER_IP}:8080${NC}"
