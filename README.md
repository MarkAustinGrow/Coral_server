# Coral Server Docker Deployment

This repository contains Docker configuration files for deploying the Coral server on a Linode server.

## Prerequisites

- A Linode server with Ubuntu 22.04 LTS
- SSH access to the Linode server
- Basic knowledge of Docker and Linux commands

## Deployment Steps

### 1. Set up a Linode server

1. Create a new Linode instance (2GB RAM minimum recommended)
2. Choose Ubuntu 22.04 LTS as the operating system
3. Configure SSH access and security settings

### 2. Install Docker on Linode

```bash
# Update system packages
sudo apt update
sudo apt upgrade -y

# Install Docker dependencies
sudo apt install -y apt-transport-https ca-certificates curl software-properties-common

# Add Docker's official GPG key
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Add Docker repository
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Add your user to the docker group (to run Docker without sudo)
sudo usermod -aG docker $USER

# Apply the group change (or log out and back in)
newgrp docker
```

### 3. Deploy the Coral Server

1. Clone this repository to your Linode server:

```bash
git clone https://github.com/yourusername/coral-server-docker.git
cd coral-server-docker
```

2. Build and run the Docker container using Docker Compose:

```bash
docker-compose up -d
```

This will:
- Build the Docker image using the Dockerfile
- Start the container in detached mode
- Map port 3001 from the container to the host
- Configure the container to restart automatically unless stopped manually

### 4. Verify the deployment

```bash
# Check if the container is running
docker ps

# Check the logs
docker logs coral-server
```

### 5. Configure firewall on Linode

Make sure to allow incoming traffic on port 3001 (for the SSE server) and port 22 (for SSH access).

## Accessing the Server

Once deployed, you can access the Coral server using:
- From the Linode itself: `http://localhost:3001`
- From the internet: `http://your-linode-ip:3001`

The server provides endpoints for:
- Registering agents
- Creating and managing conversation threads
- Sending messages to threads

## Monitoring and Maintenance

### View container stats

```bash
docker stats coral-server
```

### Update procedure

```bash
# Pull latest changes
git pull

# Rebuild and restart the container
docker-compose up -d --build
```

## Important Considerations

1. **Security**: The server currently doesn't have built-in authentication or encryption. For production use, consider:
   - Setting up a reverse proxy (like Nginx) with SSL
   - Implementing network-level security (firewall rules, VPN)

2. **Project Status**: The Coral server is in its early stages and not yet production-ready. Monitor for stability issues.

3. **Persistence**: The current implementation doesn't appear to have persistent storage. If data persistence becomes important, you'll need to add volume mounts to the Docker container.

4. **Remote Mode**: The project is working toward a "Remote-mode" that will allow agents to communicate over the internet. This may require additional configuration in the future.
