#!/bin/bash

# Script to push LangChain compatibility changes to the GitHub repository
# Usage: ./push-to-github.sh [branch-name]

# Default branch name
BRANCH_NAME=${1:-"langchain-compatibility"}

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Pushing LangChain compatibility changes to GitHub repository${NC}"

# Check if git is installed
if ! command -v git &> /dev/null; then
    echo -e "${RED}Git is not installed. Please install git and try again.${NC}"
    exit 1
fi

# Check if the current directory is a git repository
if [ ! -d ".git" ]; then
    echo -e "${RED}Current directory is not a git repository.${NC}"
    exit 1
fi

# Check if there are any uncommitted changes
if [ -n "$(git status --porcelain)" ]; then
    # Create a new branch
    echo -e "${YELLOW}Creating new branch: ${BRANCH_NAME}${NC}"
    git checkout -b "$BRANCH_NAME" || { echo -e "${RED}Failed to create branch${NC}"; exit 1; }

    # Add the modified files
    echo -e "${YELLOW}Adding modified files...${NC}"
    git add src/main/resources/application.yaml src/main/kotlin/org/coralprotocol/coralserver/Main.kt || { echo -e "${RED}Failed to add files${NC}"; exit 1; }

    # Add the new files
    echo -e "${YELLOW}Adding new files...${NC}"
    git add update-coral-server.md test-langchain-connection.py test-langchain-connection-linode.py README-langchain-compatibility.md requirements.txt deploy-to-linode.sh push-to-github.sh || { echo -e "${RED}Failed to add new files${NC}"; exit 1; }

    # Commit the changes
    echo -e "${YELLOW}Committing changes...${NC}"
    git commit -m "Add LangChain compatibility: update port to 5555 and add exampleApplication config" || { echo -e "${RED}Failed to commit changes${NC}"; exit 1; }

    # Push the changes to GitHub
    echo -e "${YELLOW}Pushing changes to GitHub...${NC}"
    git push -u origin "$BRANCH_NAME" || { echo -e "${RED}Failed to push changes${NC}"; exit 1; }

    echo -e "${GREEN}Changes pushed to GitHub repository${NC}"
    echo -e "${GREEN}Branch: ${BRANCH_NAME}${NC}"
    echo -e "${YELLOW}Next steps:${NC}"
    echo -e "1. Create a pull request on GitHub: https://github.com/Coral-Protocol/coral-server/compare/$BRANCH_NAME?expand=1"
    echo -e "2. Review the changes and merge the pull request"
    echo -e "3. Deploy the changes to the Linode server using the deploy-to-linode.sh script"
else
    echo -e "${YELLOW}No changes to commit.${NC}"
fi
