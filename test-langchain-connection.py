import asyncio
import logging
from langchain_mcp_adapters.client import MultiServerMCPClient
import urllib.parse

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

async def test_connection():
    """
    Test connection to the Coral server with LangChain expected parameters.
    """
    # Configure connection parameters
    base_url = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
    params = {
        "waitForAgents": 1,
        "agentId": "test_agent",
        "agentDescription": "Test agent for connection verification"
    }
    query_string = urllib.parse.urlencode(params)
    mcp_server_url = f"{base_url}?{query_string}"
    
    logger.info(f"Attempting to connect to: {mcp_server_url}")
    
    try:
        async with MultiServerMCPClient(
            connections={
                "coral": {
                    "transport": "sse",
                    "url": mcp_server_url,
                    "timeout": 30,
                    "sse_read_timeout": 30,
                }
            }
        ) as client:
            logger.info("Successfully connected to Coral server")
            
            # Try to list agents
            try:
                logger.info("Attempting to list agents...")
                result = await client.invoke_tool("list_agents", {})
                logger.info(f"Agents: {result}")
            except Exception as e:
                logger.error(f"Error listing agents: {e}")
            
            # Try to create a thread
            try:
                logger.info("Attempting to create a thread...")
                thread_result = await client.invoke_tool("create_thread", {
                    "name": "Test Thread",
                    "participants": ["test_agent"]
                })
                logger.info(f"Thread created: {thread_result}")
            except Exception as e:
                logger.error(f"Error creating thread: {e}")
    
    except Exception as e:
        logger.error(f"Connection error: {e}")
        return False
    
    return True

async def main():
    """
    Main function to run the connection test.
    """
    logger.info("Starting Coral server connection test")
    
    success = await test_connection()
    
    if success:
        logger.info("Connection test completed successfully")
    else:
        logger.error("Connection test failed")

if __name__ == "__main__":
    asyncio.run(main())
