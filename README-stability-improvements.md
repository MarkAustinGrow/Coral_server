# Coral Server Stability Improvements

This package contains improvements to the Coral server to address "Broken pipe" errors and enhance connection stability.

## Contents

- **Updated Source Files**:
  - `SseServer.kt`: Enhanced error handling and SSE configuration
  - `SseRoutes.kt`: Improved connection management and tracking
  - `MessageRoutes.kt`: Added transport health checks and cleanup

- **Documentation**:
  - `connection-stability-improvements.md`: Detailed explanation of the changes
  - `README-stability-improvements.md`: This file

- **Deployment**:
  - `deploy-stability-improvements.sh`: Script to deploy the changes

## Quick Start

To deploy these improvements to your Linode server:

1. Copy all files to your Linode server:
   ```bash
   scp SseServer.kt SseRoutes.kt MessageRoutes.kt deploy-stability-improvements.sh connection-stability-improvements.md root@your-linode-ip:~/Coral_server/
   ```

2. SSH into your Linode server:
   ```bash
   ssh root@your-linode-ip
   ```

3. Make the deployment script executable:
   ```bash
   cd ~/Coral_server
   chmod +x deploy-stability-improvements.sh
   ```

4. Run the deployment script:
   ```bash
   ./deploy-stability-improvements.sh
   ```

5. Test the server:
   ```bash
   python3 test_coral_client.py --server localhost:3001 --http --devmode
   ```

## Key Improvements

1. **Graceful Handling of Client Disconnections**:
   - Properly catches and handles "Broken pipe" errors
   - Prevents cascading failures when clients disconnect unexpectedly

2. **Connection Health Checks**:
   - Verifies transport is active before sending messages
   - Automatically cleans up broken connections

3. **Enhanced Logging**:
   - More informative log messages for connection events
   - Better tracking of client connections with IP and user agent information

4. **Improved Stability**:
   - Server continues running smoothly even when clients disconnect
   - Resources are properly cleaned up to prevent memory leaks

## Verification

After deploying the changes, you should see:

1. Fewer error messages in the logs
2. "Broken pipe" errors handled as warnings rather than errors
3. Improved stability when clients connect and disconnect
4. Better resource management

## Rollback

If you need to roll back the changes, the deployment script creates backups of the original files. You can restore them with:

```bash
# Replace TIMESTAMP with the actual backup timestamp
cp /root/Coral_server/backups/TIMESTAMP/SseServer.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/server/
cp /root/Coral_server/backups/TIMESTAMP/SseRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/
cp /root/Coral_server/backups/TIMESTAMP/MessageRoutes.kt /root/Coral_server/coral-server-master/src/main/kotlin/org/coralprotocol/coralserver/routes/

# Rebuild and restart
cd /root/Coral_server/coral-server-master
./gradlew build
cd /root/Coral_server
docker compose down
docker compose up -d
```

## Further Information

For a detailed explanation of the changes and the technical approach, see `connection-stability-improvements.md`.
