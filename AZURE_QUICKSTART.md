# 🚀 Quick Deploy to Azure for Students

Deploy Varutri Honeypot to Azure in 3 simple steps!

## Prerequisites
- Azure for Students account ($100 free credit)
- Hugging Face API key
- MongoDB Atlas connection string

## Option 1: Automated Deployment (Easiest) ⚡

### 1. Install Azure CLI
```bash
brew install azure-cli
```

### 2. Run the deployment script
```bash
./deploy-to-azure.sh
```

The script will:
- Login to Azure
- Create all necessary resources
- Build and deploy your application
- Configure environment variables
- Test the deployment

**Done!** Your app will be live at `https://varutri-honeypot.azurewebsites.net`

---

## Option 2: Manual Deployment 🛠️

### 1. Install Azure CLI & Login
```bash
brew install azure-cli
az login
```

### 2. Create Azure Resources
```bash
# Create resource group
az group create --name varutri-honeypot-rg --location eastus

# Create app service plan (FREE tier)
az appservice plan create \
  --name varutri-honeypot-plan \
  --resource-group varutri-honeypot-rg \
  --sku F1 \
  --is-linux

# Create web app
az webapp create \
  --resource-group varutri-honeypot-rg \
  --plan varutri-honeypot-plan \
  --name varutri-honeypot \
  --runtime "JAVA:17-java17"
```

### 3. Configure Environment Variables
```bash
az webapp config appsettings set \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --settings \
    HUGGINGFACE_API_KEY="your_key_here" \
    MONGODB_URI="your_mongodb_uri" \
    MONGODB_DATABASE="varutri_honeypot" \
    VARUTRI_API_KEY="varutri_shield_2026"
```

### 4. Build and Deploy
```bash
# Build the application
mvn clean package -DskipTests

# Deploy to Azure
az webapp deploy \
  --resource-group varutri-honeypot-rg \
  --name varutri-honeypot \
  --src-path target/honeypot-1.0.0.jar \
  --type jar
```

### 5. Test Your Deployment
```bash
curl https://varutri-honeypot.azurewebsites.net/actuator/health
```

---

## Useful Commands 📝

### View Logs
```bash
az webapp log tail --resource-group varutri-honeypot-rg --name varutri-honeypot
```

### Restart App
```bash
az webapp restart --resource-group varutri-honeypot-rg --name varutri-honeypot
```

### Stop App (Save Credits)
```bash
az webapp stop --resource-group varutri-honeypot-rg --name varutri-honeypot
```

### Start App
```bash
az webapp start --resource-group varutri-honeypot-rg --name varutri-honeypot
```

### Delete Everything
```bash
az group delete --name varutri-honeypot-rg --yes --no-wait
```

---

## Testing Your API 🧪

```bash
curl -X POST https://varutri-honeypot.azurewebsites.net/api/chat \
  -H "x-api-key: varutri_shield_2026" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "azure-test-001",
    "message": "Hello, I have a special investment opportunity",
    "conversationHistory": []
  }'
```

---

## Troubleshooting 🔧

### App won't start?
```bash
# Check logs
az webapp log tail -g varutri-honeypot-rg -n varutri-honeypot

# Restart
az webapp restart -g varutri-honeypot-rg -n varutri-honeypot
```

### MongoDB connection failed?
- Verify MongoDB URI is correct
- Add `0.0.0.0/0` to MongoDB Atlas IP whitelist (for testing)

### API returns 401 Unauthorized?
- Check `x-api-key` header is set to `varutri_shield_2026`
- Verify `VARUTRI_API_KEY` environment variable is configured

---

## Cost Management 💰

**Azure for Students:**
- $100 free credit
- F1 (Free tier): $0/month - Perfect for demos!
- B1 (Basic): ~$13/month - Better performance
- Monitor usage: https://www.microsoftazuresponsorships.com/

**To save credits when not in use:**
```bash
az webapp stop -g varutri-honeypot-rg -n varutri-honeypot
```

---

## Need More Help? 📚

- **Detailed Guide**: See [AZURE_SETUP.md](AZURE_SETUP.md)
- **Azure Docs**: https://docs.microsoft.com/azure
- **Azure for Students**: https://aka.ms/azureforstudents

---

**Ready to deploy?** Run `./deploy-to-azure.sh` and you're done! 🎉
