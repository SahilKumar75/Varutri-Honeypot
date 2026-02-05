# Deploy Varutri Honeypot to Azure for Students

This guide will help you deploy the Varutri Honeypot application to Azure using your Azure for Students account ($100 free credit).

## Prerequisites

- Azure for Students account ([Sign up here](https://azure.microsoft.com/en-us/free/students/))
- Azure CLI installed ([Download here](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli))
- Java 17+
- Maven
- Hugging Face API key

## Architecture Overview

We'll deploy:
- **Azure App Service** (Linux, Java 17) - Hosts the Spring Boot application
- **MongoDB Atlas** - Database (already configured)
- **Application Insights** - Monitoring and logging (optional)

## Step 1: Install Azure CLI

### macOS
```bash
brew update && brew install azure-cli
```

### Verify Installation
```bash
az --version
```

## Step 2: Login to Azure

```bash
az login
```

This will open your browser. Sign in with your Azure for Students account.

### Set your subscription (if you have multiple)
```bash
# List subscriptions
az account list --output table

# Set the active subscription
az account set --subscription "Azure for Students"
```

## Step 3: Create Resource Group

```bash
# Create a resource group in a region close to you
az group create \
  --name varutri-honeypot-rg \
  --location eastus
```

Available locations: `eastus`, `westus`, `centralindia`, `southeastasia`, `westeurope`

## Step 4: Build the Application

```bash
# Clean and package the application
mvn clean package -DskipTests

# Verify the JAR file was created
ls -lh target/*.jar
```

## Step 5: Create Azure App Service Plan

```bash
# Create a FREE Linux App Service Plan (F1 tier)
az appservice plan create \
  --name varutri-honeypot-plan \
  --resource-group varutri-honeypot-rg \
  --sku F1 \
  --is-linux
```

**Note:** F1 (Free tier) is sufficient for development/demo. For production, use B1 (Basic) or higher.

## Step 6: Create Web App

```bash
# Create the web app with Java 17
az webapp create \
  --resource-group varutri-honeypot-rg \
  --plan varutri-honeypot-plan \
  --name varutri-honeypot \
  --runtime "JAVA:17-java17"
```

**Important:** The app name must be globally unique. If `varutri-honeypot` is taken, try:
- `varutri-honeypot-2026`
- `varutri-honeypot-yourname`
- `varutri-honeypot-team`

Your app URL will be: `https://varutri-honeypot.azurewebsites.net`

## Step 7: Configure Environment Variables

```bash
# Set Hugging Face API Key
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings HUGGINGFACE_API_KEY="your_huggingface_api_key_here"

# Set MongoDB URI (from your MongoDB Atlas)
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings MONGODB_URI="your_mongodb_connection_string"

# Set MongoDB Database Name
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings MONGODB_DATABASE="varutri_honeypot"

# Set API Key
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings VARUTRI_API_KEY="varutri_shield_2026"

# Set Hackathon Callback URL (if needed)
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings HACKATHON_CALLBACK_URL="https://your-callback-url.com"
```

### All-in-One Command
```bash
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings \
    HUGGINGFACE_API_KEY="hf_xxxxxxxxxxxxx" \
    MONGODB_URI="mongodb+srv://username:password@cluster0.qqtqboz.mongodb.net/?retryWrites=true&w=majority" \
    MONGODB_DATABASE="varutri_honeypot" \
    VARUTRI_API_KEY="varutri_shield_2026" \
    HACKATHON_CALLBACK_URL="https://hackathon.example.com/callback"
```

## Step 8: Configure Java Runtime

```bash
# Set Java version and configure startup
az webapp config set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --java-version "17" \
  --java-container "JAVA" \
  --java-container-version "17"
```

## Step 9: Deploy Application

### Option A: Deploy via Maven Plugin (Recommended)

1. Add Azure Web App Maven Plugin to `pom.xml`:

```xml
<!-- Add inside <build><plugins> section -->
<plugin>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>azure-webapp-maven-plugin</artifactId>
    <version>2.12.0</version>
    <configuration>
        <schemaVersion>v2</schemaVersion>
        <resourceGroup>varutri-honeypot-rg</resourceGroup>
        <appName>varutri-honeypot</appName>
        <region>eastus</region>
        <runtime>
            <os>Linux</os>
            <javaVersion>Java 17</javaVersion>
            <webContainer>Java SE</webContainer>
        </runtime>
        <deployment>
            <resources>
                <resource>
                    <directory>${project.basedir}/target</directory>
                    <includes>
                        <include>*.jar</include>
                    </includes>
                </resource>
            </resources>
        </deployment>
    </configuration>
</plugin>
```

2. Deploy:
```bash
mvn azure-webapp:deploy
```

### Option B: Deploy via Azure CLI

```bash
# Deploy the JAR file
az webapp deploy \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --src-path target/honeypot-1.0.0.jar \
  --type jar
```

### Option C: Deploy via ZIP

```bash
# Create deployment package
cd target
zip -r ../app.zip *.jar

# Deploy
az webapp deployment source config-zip \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --src ../app.zip
```

## Step 10: Configure Startup Command (If Needed)

```bash
az webapp config set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --startup-file "java -jar /home/site/wwwroot/honeypot-1.0.0.jar"
```

## Step 11: Enable Logging

```bash
# Enable application logging
az webapp log config \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --application-logging filesystem \
  --level information

# Stream logs
az webapp log tail \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot
```

## Step 12: Test Your Deployment

```bash
# Get your app URL
az webapp show \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --query defaultHostName \
  --output tsv
```

### Test the API
```bash
# Replace with your actual Azure URL
curl -X POST https://varutri-honeypot.azurewebsites.net/api/chat \
  -H "x-api-key: varutri_shield_2026" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "azure-test-001",
    "message": "Hello, I have a special investment opportunity",
    "conversationHistory": []
  }'
```

### Test Health Endpoint
```bash
curl https://varutri-honeypot.azurewebsites.net/actuator/health
```

## Step 13: Scale Up (Optional)

If you need better performance:

```bash
# Upgrade to Basic B1 plan (more memory and CPU)
az appservice plan update \
  --name varutri-honeypot-plan \
  --resource-group varutri-honeypot-rg \
  --sku B1
```

**Azure for Students Credit Usage:**
- F1 (Free): $0/month
- B1 (Basic): ~$13/month
- S1 (Standard): ~$70/month

## Troubleshooting

### View Application Logs
```bash
# Stream live logs
az webapp log tail \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot

# Download logs
az webapp log download \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --log-file logs.zip
```

### Check Application Settings
```bash
az webapp config appsettings list \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot
```

### Restart Application
```bash
az webapp restart \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot
```

### SSH into Container
```bash
az webapp ssh \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot
```

### Common Issues

**1. Application Won't Start**
- Check logs: `az webapp log tail`
- Verify Java version: Should be Java 17
- Check environment variables are set correctly

**2. MongoDB Connection Failed**
- Verify MongoDB URI is correct
- Check MongoDB Atlas allows Azure IPs (add `0.0.0.0/0` to IP whitelist for testing)

**3. 502 Bad Gateway**
- Application is starting (wait 2-3 minutes)
- Check logs for startup errors
- Verify JAR file is valid

**4. API Key Authentication Failed**
- Verify `VARUTRI_API_KEY` environment variable is set
- Use correct header: `x-api-key: varutri_shield_2026`

## CI/CD Setup (Optional)

### GitHub Actions Deployment

1. Get publish profile:
```bash
az webapp deployment list-publishing-profiles \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --xml
```

2. Add the publish profile as a GitHub secret named `AZURE_WEBAPP_PUBLISH_PROFILE`

3. Create `.github/workflows/azure-deploy.yml`:

```yaml
name: Deploy to Azure

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up Java 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Build with Maven
      run: mvn clean package -DskipTests
    
    - name: Deploy to Azure Web App
      uses: azure/webapps-deploy@v2
      with:
        app-name: 'varutri-honeypot'
        publish-profile: ${{ secrets.AZURE_WEBAPP_PUBLISH_PROFILE }}
        package: '${{ github.workspace }}/target/*.jar'
```

## Monitoring with Application Insights (Optional)

```bash
# Create Application Insights
az monitor app-insights component create \
  --app varutri-honeypot-insights \
  --location eastus \
  --resource-group varutri-honeypot-rg

# Get instrumentation key
az monitor app-insights component show \
  --app varutri-honeypot-insights \
  --resource-group varutri-honeypot-rg \
  --query instrumentationKey

# Add to app settings
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings APPINSIGHTS_INSTRUMENTATIONKEY="your-instrumentation-key"
```

## Custom Domain (Optional)

```bash
# Add custom domain
az webapp config hostname add \
  --resource-group varutri-honeypot-rg \
  --webapp-name varutri-honeypot \
  --hostname www.yourdomain.com
```

## Clean Up Resources

When you're done testing:

```bash
# Delete the entire resource group
az group delete \
  --name varutri-honeypot-rg \
  --yes --no-wait
```

## Cost Management

Monitor your Azure for Students credit:
- Visit: https://www.microsoftazuresponsorships.com/
- Check remaining balance
- Set up billing alerts

## Support

- Azure for Students Support: https://aka.ms/azureforstudents
- Azure Documentation: https://docs.microsoft.com/azure
- Spring Boot on Azure: https://docs.microsoft.com/azure/developer/java/spring-framework/

## Quick Reference

### Your Application URLs
- **Web App**: `https://varutri-honeypot.azurewebsites.net`
- **Health Check**: `https://varutri-honeypot.azurewebsites.net/actuator/health`
- **API Endpoint**: `https://varutri-honeypot.azurewebsites.net/api/chat`

### Useful Commands
```bash
# Status
az webapp show -g varutri-honeypot-rg -n varutri-honeypot --query state

# Logs
az webapp log tail -g varutri-honeypot-rg -n varutri-honeypot

# Restart
az webapp restart -g varutri-honeypot-rg -n varutri-honeypot

# Stop
az webapp stop -g varutri-honeypot-rg -n varutri-honeypot

# Start
az webapp start -g varutri-honeypot-rg -n varutri-honeypot
```

---

**Ready to Deploy?** Start with Step 1 and follow through Step 12. Good luck! 🚀
