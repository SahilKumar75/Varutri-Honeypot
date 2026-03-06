#!/bin/bash

# ============================================
# Varutri Honeypot - Azure Deployment Script
# ============================================
# This script automates the deployment to Azure for Students

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
RESOURCE_GROUP="varutri-honeypot-rg"
APP_SERVICE_PLAN="varutri-honeypot-plan"
APP_NAME="varutri-honeypot"
LOCATION="southeastasia"  # Southeast Asia - reliable for Azure for Students
JAVA_VERSION="17"
SKU="F1"  # Free tier - change to B1 for better performance

# GitHub Configuration
GITHUB_REPO="https://github.com/SahilKumar75/Varutri-Honeypot.git"
GITHUB_BRANCH="azure-backend"

# Print colored message
print_message() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check if Azure CLI is installed
if ! command -v az &> /dev/null; then
    print_error "Azure CLI is not installed. Please install it first:"
    echo "  macOS: brew install azure-cli"
    echo "  Visit: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli"
    exit 1
fi

print_message "Azure CLI found: $(az --version | head -n 1)"

# Check if logged in to Azure
print_message "Checking Azure login status..."
if ! az account show &> /dev/null; then
    print_warning "Not logged in to Azure. Initiating login..."
    az login
else
    print_message "Already logged in to Azure"
    az account show --query "{Subscription:name, ID:id, User:user.name}" -o table
fi

# Prompt for environment variables
print_message "\n=== Environment Variables Setup ==="
echo "Please provide the following configuration values:"
echo ""

read -p "Hugging Face API Key: " HUGGINGFACE_API_KEY
read -p "MongoDB Connection URI: " MONGODB_URI
read -p "MongoDB Database Name [varutri_honeypot]: " MONGODB_DATABASE
MONGODB_DATABASE=${MONGODB_DATABASE:-varutri_honeypot}

read -p "Varutri API Key [varutri_shield_2026]: " VARUTRI_API_KEY
VARUTRI_API_KEY=${VARUTRI_API_KEY:-varutri_shield_2026}

read -p "Hackathon Callback URL (optional): " HACKATHON_CALLBACK_URL

# Select region
echo ""
print_message "Select Azure region for deployment:"
echo "  1) southeastasia    - Southeast Asia (recommended for Azure for Students)"
echo "  2) centralindia     - Central India"
echo "  3) eastus2          - East US 2"
echo "  4) westeurope       - West Europe"
echo "  5) uksouth          - UK South"
read -p "Enter choice [1]: " REGION_CHOICE
REGION_CHOICE=${REGION_CHOICE:-1}

case $REGION_CHOICE in
    1) LOCATION="southeastasia" ;;
    2) LOCATION="centralindia" ;;
    3) LOCATION="eastus2" ;;
    4) LOCATION="westeurope" ;;
    5) LOCATION="uksouth" ;;
    *) LOCATION="southeastasia" ;;
esac
print_message "Selected region: ${LOCATION}"

# Confirm app name
echo ""
read -p "App Name [${APP_NAME}]: " CUSTOM_APP_NAME
if [ ! -z "$CUSTOM_APP_NAME" ]; then
    APP_NAME=$CUSTOM_APP_NAME
fi

print_message "Using App Name: ${APP_NAME}"
print_message "Your app will be available at: https://${APP_NAME}.azurewebsites.net"
echo ""

read -p "Continue with deployment? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_warning "Deployment cancelled."
    exit 0
fi

# Step 1: Create Resource Group
print_message "\n=== Step 1: Creating Resource Group ==="
if az group show --name $RESOURCE_GROUP &> /dev/null; then
    print_warning "Resource group '$RESOURCE_GROUP' already exists. Skipping creation."
else
    az group create --name $RESOURCE_GROUP --location $LOCATION
    print_message "Resource group created successfully"
fi

# Step 2: Create App Service Plan
print_message "\n=== Step 2: Creating App Service Plan ==="
if az appservice plan show --name $APP_SERVICE_PLAN --resource-group $RESOURCE_GROUP &> /dev/null; then
    print_warning "App Service Plan '$APP_SERVICE_PLAN' already exists. Skipping creation."
else
    az appservice plan create \
        --name $APP_SERVICE_PLAN \
        --resource-group $RESOURCE_GROUP \
        --sku $SKU \
        --is-linux
    print_message "App Service Plan created successfully"
fi

# Step 3: Create Web App
print_message "\n=== Step 3: Creating Web App ==="
if az webapp show --name $APP_NAME --resource-group $RESOURCE_GROUP &> /dev/null; then
    print_warning "Web App '$APP_NAME' already exists. Skipping creation."
else
    az webapp create \
        --resource-group $RESOURCE_GROUP \
        --plan $APP_SERVICE_PLAN \
        --name $APP_NAME \
        --runtime "JAVA:17-java17"
    print_message "Web App created successfully"
fi

# Step 4: Configure Environment Variables
print_message "\n=== Step 4: Configuring Environment Variables ==="
SETTINGS="HUGGINGFACE_API_KEY=$HUGGINGFACE_API_KEY MONGODB_URI=$MONGODB_URI MONGODB_DATABASE=$MONGODB_DATABASE VARUTRI_API_KEY=$VARUTRI_API_KEY"

if [ ! -z "$HACKATHON_CALLBACK_URL" ]; then
    SETTINGS="$SETTINGS HACKATHON_CALLBACK_URL=$HACKATHON_CALLBACK_URL"
fi

az webapp config appsettings set \
    --resource-group $RESOURCE_GROUP \
    --name $APP_NAME \
    --settings $SETTINGS \
    --output none

print_message "Environment variables configured"

# Step 5: Configure GitHub Deployment
print_message "\n=== Step 5: Configuring GitHub Deployment ==="
print_message "Repository: $GITHUB_REPO"
print_message "Branch: $GITHUB_BRANCH"

# Enable GitHub deployment
az webapp deployment source config \
    --resource-group $RESOURCE_GROUP \
    --name $APP_NAME \
    --repo-url $GITHUB_REPO \
    --branch $GITHUB_BRANCH \
    --manual-integration

print_message "GitHub deployment configured"

# Step 6: Configure Build (Maven)
print_message "\n=== Step 6: Configuring Maven Build ==="

# Set build configuration for Java Maven app
az webapp config appsettings set \
    --resource-group $RESOURCE_GROUP \
    --name $APP_NAME \
    --settings \
        SCM_DO_BUILD_DURING_DEPLOYMENT=true \
        ENABLE_ORYX_BUILD=true \
        MAVEN_OPTS="-Xmx512m" \
    --output none

print_message "Maven build configured"
8: Enable Logging
print_message "\n=== Step 8t
print_message "\n=== Step 7: Triggering Deployment from GitHub ==="
print_message "Syncing code from GitHub..."

az webapp deployment source sync \
    --resource-group $RESOURCE_GROUP \
    --name $APP_NAME

print_message "Deployment triggered successfully"

# Step 7: Enable Logging
print_message "\n=== Step 7: Enabling Logging ==="
az webapp log config \
    --resource-group $RESOURCE_GROUP \
    --name $APP_NAME \
    --application-logging filesystem \
    --level information \
    --output none

print_message "Logging enabled"

# Step 9: Wait for app to start
print_message "\n=== Step 9: Waiting for Application to Start ==="
print_message "This may take 2-3 minutes..."
sleep 30

APP_URL="https://${APP_NAME}.azurewebsites.net"

# Success message
print_message "\n${GREEN}========================================${NC}"
print_message "${GREEN}✓ Deployment Complete!${NC}"
print_message "${GREEN}========================================${NC}"
echo ""
echo "Your application is now running on Azure!"
echo ""
echo "📍 Application URL: ${APP_URL}"
echo "🏥 Health Check:    ${APP_URL}/actuator/health"
echo "🔌 API Endpoint:    ${APP_URL}/api/chat"
echo ""
print_message "Testing health endpoint..."
sleep 30  # Give app more time to fully start

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" ${APP_URL}/actuator/health || echo "000")
if [ "$HTTP_STATUS" = "200" ]; then
    print_message "✓ Health check passed! Application is running."
else
    print_warning "Health check returned status: $HTTP_STATUS"
    print_message "The application may still be starting. Please wait a few more minutes."
fi

echo ""
print_message "To view logs, run:"
echo "  az webapp log tail --resource-group $RESOURCE_GROUP --name $APP_NAME"
echo ""
print_message "To test the API:"
echo "  curl -X POST ${APP_URL}/api/chat \\"
echo "    -H 'x-api-key: ${VARUTRI_API_KEY}' \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"sessionId\":\"test-001\",\"message\":\"Hello\",\"conversationHistory\":[]}'"
echo ""
print_message "To stop the app (save credits):"
echo "  az webapp stop --resource-group $RESOURCE_GROUP --name $APP_NAME"
echo ""
print_message "Deployment script completed successfully! 🚀"
