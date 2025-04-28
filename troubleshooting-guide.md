# Coral Server Troubleshooting Guide

This guide provides detailed troubleshooting steps for common issues with the Coral server deployment on Linode.

## 404 Error When Registering an Agent

If you're getting a 404 error when trying to register an agent, it means the server endpoint couldn't be found. Here are some steps to diagnose and fix the issue:

### 1. Check Server Status

First, verify that the Coral server is running:

```bash
# SSH into your Linode server
ssh root@172.237.124.61

# Check if the Docker container is running
docker ps

# If it's not running, start it
docker compose -f docker-compose-debug.yml up -d
```

### 2. Check Server Logs

Look at the server logs to see if there are any errors:

```bash
docker logs coral_server-coral-server-1
```

Look for messages like:
- "Starting sse server on port 3001"
- Any error messages related to routing or endpoints

### 3. Test Local Access

Try accessing the server directly on the Linode server to bypass any Nginx configuration issues:

```bash
# On the Linode server
curl http://localhost:3001/default-app/public/test-session/sse
```

If this works but external access doesn't, it's likely an Nginx configuration issue.

### 4. Check Nginx Configuration

Verify that Nginx is properly configured to forward requests to the Coral server:

```bash
# Check Nginx configuration
cat /etc/nginx/sites-enabled/coral-server

# Check Nginx status
systemctl status nginx

# Check Nginx logs
tail -f /var/log/nginx/error.log
```

The Nginx configuration should include a location block that forwards requests to the Coral server on port 3001.

### 5. Test Different URL Formats

The Coral server might be expecting a different URL format. Try these variations:

```bash
# Test with different URL formats
python test_coral_client.py --server coral.pushcollective.club --http
python test_coral_client.py --server coral.pushcollective.club:3001 --http
python test_coral_client.py --server 172.237.124.61 --http
python test_coral_client.py --server 172.237.124.61:3001 --http
```

### 6. Check Firewall Settings

Make sure the necessary ports are open:

```bash
# Check firewall status
sudo ufw status

# If needed, allow the ports
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 3001/tcp
```

### 7. Verify DNS Configuration

Make sure the domain name is correctly pointing to your Linode server:

```bash
# Check DNS resolution
nslookup coral.pushcollective.club
```

### 8. Test with Direct IP Address

Try connecting directly to the IP address instead of the domain name:

```bash
python test_coral_client.py --server 172.237.124.61 --http
```

## Debugging the URL Structure

The Coral server expects URLs in this format:
```
http://server:port/{applicationId}/{privacyKey}/{sessionId}/sse
```

If you're getting 404 errors, it might be because:

1. The applicationId is incorrect (try "default-app")
2. The privacyKey is incorrect (try "public")
3. The sessionId format is incorrect (try a simple string like "test-session")

You can modify these parameters in the test client:

```bash
python test_coral_client.py --server coral.pushcollective.club --app default-app --key public --session test-session
```

## Advanced Debugging

### Enable Verbose Logging in the Test Client

Modify the test_coral_client.py file to enable more verbose logging:

```python
import logging
logging.basicConfig(level=logging.DEBUG)
```

### Inspect Network Traffic

Use tools like Wireshark or tcpdump to inspect the network traffic:

```bash
# On the Linode server
sudo tcpdump -i any port 3001 -vv
```

### Check for SSL/TLS Issues

If you're using HTTPS, there might be SSL/TLS configuration issues:

```bash
# Test SSL connection
openssl s_client -connect coral.pushcollective.club:443
```

## Common Solutions

1. **Incorrect Endpoint for Tool Calls**:
   
   One of the most common issues is sending tool calls (POST requests) to the wrong endpoint. The SSE endpoint (with "/sse" suffix) should only be used for GET requests to establish the server-sent events connection.
   
   ```
   # CORRECT:
   # For SSE connection (GET):
   https://coral.pushcollective.club/default-app/public/session1/sse
   
   # For tool calls (POST):
   https://coral.pushcollective.club/default-app/public/session1
   ```
   
   If you're getting 404 errors when trying to register an agent or perform other tool calls, make sure you're sending POST requests to the base URL without the "/sse" suffix.

2. **Restart the Coral server**:
   ```bash
   docker compose down
   docker compose -f docker-compose-debug.yml up -d
   ```

2. **Restart Nginx**:
   ```bash
   sudo systemctl restart nginx
   ```

3. **Update Nginx configuration**:
   Create or modify /etc/nginx/sites-available/coral-server:
   ```nginx
   server {
       listen 80;
       server_name coral.pushcollective.club;
       
       location / {
           proxy_pass http://localhost:3001;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection 'upgrade';
           proxy_set_header Host $host;
           proxy_cache_bypass $http_upgrade;
       }
   }
   ```
   Then enable it:
   ```bash
   sudo ln -s /etc/nginx/sites-available/coral-server /etc/nginx/sites-enabled/
   sudo systemctl restart nginx
   ```

4. **Check for conflicting services**:
   ```bash
   sudo netstat -tulpn | grep 3001
   ```

5. **Verify Docker network**:
   ```bash
   docker network ls
   docker network inspect coral_server_default
   ```

If you continue to experience issues, please provide the specific error messages and logs to help diagnose the problem further.
