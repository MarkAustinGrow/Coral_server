# Updating Coral Server on Linode for LangChain Compatibility

This document outlines the steps needed to update the Coral server running on the Linode server to make it compatible with LangChain agents.

## Changes Made Locally

We've made the following changes to the local copy of the code:

1. Updated `application.yaml` to include the LangChain expected application ID and privacy key:
   ```yaml
   applications:
     - id: "exampleApplication"
       name: "Example Application"
       description: "Application for LangChain agents"
       privacyKeys:
         - "privkey"
         - "public"
     - id: "default-app"
       name: "Default Application"
       description: "Default application for testing"
       privacyKeys:
         - "default-key"
         - "public"
   ```

2. Updated `Main.kt` to change the default port from 3001 to 5555:
   ```kotlin
   /**
    * Start sse-server mcp on port 5555.
    */
   val port = args.getOrNull(1)?.toIntOrNull() ?: 5555
   ```

## Steps to Update the Linode Server

1. **SSH into the Linode server**:
   ```bash
   ssh root@coral.pushcollective.club
   ```

2. **Navigate to the Coral server directory**:
   ```bash
   cd /opt/coral-server
   ```

3. **Backup the current configuration**:
   ```bash
   cp src/main/resources/application.yaml src/main/resources/application.yaml.bak
   cp src/main/kotlin/org/coralprotocol/coralserver/Main.kt src/main/kotlin/org/coralprotocol/coralserver/Main.kt.bak
   ```

4. **Update the application.yaml file**:
   ```bash
   nano src/main/resources/application.yaml
   ```
   Add the new application configuration:
   ```yaml
   applications:
     - id: "exampleApplication"
       name: "Example Application"
       description: "Application for LangChain agents"
       privacyKeys:
         - "privkey"
         - "public"
     - id: "default-app"
       name: "Default Application"
       description: "Default application for testing"
       privacyKeys:
         - "default-key"
         - "public"
   ```

5. **Update the Main.kt file**:
   ```bash
   nano src/main/kotlin/org/coralprotocol/coralserver/Main.kt
   ```
   Change the default port from 3001 to 5555:
   ```kotlin
   val port = args.getOrNull(1)?.toIntOrNull() ?: 5555
   ```
   Also update the comment to reflect the new default port:
   ```kotlin
   /**
    * Start sse-server mcp on port 5555.
    */
   ```

6. **Build the project**:
   ```bash
   ./gradlew build
   ```

7. **Restart the Coral server service**:
   ```bash
   systemctl restart coral-server
   ```

8. **Verify the server is running on the new port**:
   ```bash
   systemctl status coral-server
   ```
   Check that it's running with the new port:
   ```
   /usr/bin/java -jar /opt/coral-server/build/libs/coral-server-1.0-SNAPSHOT.jar --sse-server 5555
   ```

9. **Test the connection with a simple client**:
   ```bash
   curl -v http://localhost:5555/devmode/exampleApplication/privkey/session1/sse
   ```

## Alternative: Update the systemd Service File

If the server is started via systemd with a specific port, you'll need to update the service file:

1. **Edit the systemd service file**:
   ```bash
   systemctl edit coral-server
   ```

2. **Update the ExecStart line**:
   ```
   [Service]
   ExecStart=/usr/bin/java -jar /opt/coral-server/build/libs/coral-server-1.0-SNAPSHOT.jar --sse-server 5555
   ```

3. **Reload systemd and restart the service**:
   ```bash
   systemctl daemon-reload
   systemctl restart coral-server
   ```

## Troubleshooting

If you encounter issues after updating:

1. **Check the logs**:
   ```bash
   journalctl -u coral-server -f
   ```

2. **Revert to the backup if needed**:
   ```bash
   cp src/main/resources/application.yaml.bak src/main/resources/application.yaml
   cp src/main/kotlin/org/coralprotocol/coralserver/Main.kt.bak src/main/kotlin/org/coralprotocol/coralserver/Main.kt
   ./gradlew build
   systemctl restart coral-server
   ```

3. **Verify port availability**:
   ```bash
   netstat -tuln | grep 5555
   ```
   If the port is already in use, you may need to choose a different port or stop the service using that port.
