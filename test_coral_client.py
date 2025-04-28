import requests
import json
import sseclient
import time
import argparse

def main():
    # Parse command line arguments
    parser = argparse.ArgumentParser(description='Test client for Coral server')
    parser.add_argument('--server', default='coral.pushcollective.club', help='Coral server hostname')
    parser.add_argument('--app', default='default-app', help='Application ID')
    parser.add_argument('--key', default='public', help='Privacy key')
    parser.add_argument('--session', default='test-session', help='Session ID')
    args = parser.parse_args()
    
    # Construct the URL
    base_url = f"https://{args.server}/{args.app}/{args.key}/{args.session}"
    sse_url = f"{base_url}/sse"
    message_url = base_url
    
    # Connect to the Coral server
    headers = {"Accept": "text/event-stream"}
    
    print(f"Connecting to {sse_url}...")
    try:
        # Establish SSE connection
        response = requests.get(sse_url, headers=headers, stream=True)
        
        if response.status_code != 200:
            print(f"Error connecting to server: {response.status_code} {response.reason}")
            print(f"Response: {response.text}")
            return
            
        client = sseclient.SSEClient(response)
        print("Connected successfully!")
        
        # Register an agent
        print("\nRegistering agent...")
        register_message = {
            "type": "tool_call",
            "tool": "register_agent",
            "args": {
                "name": "TestAgent",
                "description": "A test agent for verifying server functionality"
            }
        }
        
        print(f"Sending: {json.dumps(register_message, indent=2)}")
        register_response = requests.post(
            message_url, 
            headers={"Content-Type": "application/json"},
            json=register_message
        )
        print(f"Response status: {register_response.status_code}")
        
        try:
            response_json = register_response.json()
            print(f"Response: {json.dumps(response_json, indent=2)}")
        except:
            print(f"Response: {register_response.text}")
        
        # Listen for events
        print("\nListening for events (press Ctrl+C to stop)...")
        for event in client.events():
            print(f"Received: {event.data}")
            try:
                data = json.loads(event.data)
                if data.get("type") == "tool_response" and data.get("tool") == "register_agent":
                    agent_id = data.get("result", {}).get("agent_id")
                    if agent_id:
                        print(f"\n✅ Successfully registered agent with ID: {agent_id}")
                        
                        # Create a thread
                        print("\nCreating a thread...")
                        create_thread_message = {
                            "type": "tool_call",
                            "tool": "create_thread",
                            "args": {
                                "participants": [agent_id],
                                "metadata": {
                                    "topic": "Test thread"
                                }
                            }
                        }
                        
                        print(f"Sending: {json.dumps(create_thread_message, indent=2)}")
                        thread_response = requests.post(
                            message_url, 
                            headers={"Content-Type": "application/json"},
                            json=create_thread_message
                        )
                        
                        try:
                            thread_json = thread_response.json()
                            print(f"Response: {json.dumps(thread_json, indent=2)}")
                        except:
                            print(f"Response: {thread_response.text}")
            except json.JSONDecodeError:
                print("Error parsing event data as JSON")
            except Exception as e:
                print(f"Error processing event: {e}")
            
    except KeyboardInterrupt:
        print("\nTest client stopped by user")
    except Exception as e:
        print(f"\nError: {e}")
    
if __name__ == "__main__":
    main()
