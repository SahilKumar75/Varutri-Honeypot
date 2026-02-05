# Azure for Students Deployment Checklist ✅

Use this checklist to ensure a smooth deployment to Azure.

## Pre-Deployment ☑️

- [ ] Sign up for Azure for Students account
  - Visit: https://azure.microsoft.com/en-us/free/students/
  - Verify with your student email (.edu)
  - Get $100 free credit

- [ ] Install Azure CLI
  ```bash
  brew install azure-cli
  ```

- [ ] Get Hugging Face API Key
  - Visit: https://huggingface.co/settings/tokens
  - Create new token (read access is sufficient)
  - Copy and save it securely

- [ ] Setup MongoDB Atlas
  - Already have it? Great!
  - Copy your connection string
  - Format: `mongodb+srv://username:password@cluster.mongodb.net/...`

## Deployment ☑️

### Option A: Automated Script (Easiest)
- [ ] Navigate to project directory
  ```bash
  cd /path/to/Varutri-Honeypot
  ```

- [ ] Run deployment script
  ```bash
  ./deploy-to-azure.sh
  ```

- [ ] Provide required information when prompted:
  - [ ] Hugging Face API Key
  - [ ] MongoDB Connection URI
  - [ ] MongoDB Database Name (default: `varutri_honeypot`)
  - [ ] API Key (default: `varutri_shield_2026`)

- [ ] Wait for deployment to complete (3-5 minutes)

### Option B: Manual Deployment
- [ ] Follow steps in [AZURE_QUICKSTART.md](AZURE_QUICKSTART.md)

## Post-Deployment ☑️

- [ ] Test health endpoint
  ```bash
  curl https://your-app-name.azurewebsites.net/actuator/health
  ```
  Expected: `{"status":"UP"}`

- [ ] Test chat API
  ```bash
  curl -X POST https://your-app-name.azurewebsites.net/api/chat \
    -H "x-api-key: varutri_shield_2026" \
    -H "Content-Type: application/json" \
    -d '{"sessionId":"test-001","message":"Hello","conversationHistory":[]}'
  ```

- [ ] Check application logs
  ```bash
  az webapp log tail -g varutri-honeypot-rg -n your-app-name
  ```

- [ ] Save your app URL
  - URL: `https://your-app-name.azurewebsites.net`
  - API: `https://your-app-name.azurewebsites.net/api/chat`

## Configuration Verification ☑️

- [ ] Environment variables are set correctly
  ```bash
  az webapp config appsettings list -g varutri-honeypot-rg -n your-app-name
  ```

- [ ] MongoDB connection is working
  - Check logs for "MongoDB connection successful" message

- [ ] Hugging Face API is accessible
  - Test with a chat request

## Monitoring & Maintenance ☑️

- [ ] Set up cost alerts
  - Visit: https://www.microsoftazuresponsorships.com/
  - Monitor remaining credit

- [ ] Enable Application Insights (Optional)
  ```bash
  az monitor app-insights component create \
    --app varutri-honeypot-insights \
    --location eastus \
    --resource-group varutri-honeypot-rg
  ```

- [ ] Stop app when not in use to save credits
  ```bash
  az webapp stop -g varutri-honeypot-rg -n your-app-name
  ```

## Troubleshooting ☑️

If something goes wrong:

- [ ] Check application logs
  ```bash
  az webapp log tail -g varutri-honeypot-rg -n your-app-name
  ```

- [ ] Verify Java version
  ```bash
  az webapp config show -g varutri-honeypot-rg -n your-app-name --query javaVersion
  ```

- [ ] Restart the application
  ```bash
  az webapp restart -g varutri-honeypot-rg -n your-app-name
  ```

- [ ] Check MongoDB Atlas IP whitelist
  - Add `0.0.0.0/0` for testing (allows all IPs)
  - Or get Azure outbound IPs and whitelist them

- [ ] Verify environment variables
  ```bash
  az webapp config appsettings list -g varutri-honeypot-rg -n your-app-name
  ```

## Common Issues & Solutions 🔧

### Issue: 502 Bad Gateway
**Solution:** App is starting. Wait 2-3 minutes and try again.

### Issue: MongoDB Connection Failed
**Solution:** 
- Check connection string is correct
- Verify MongoDB Atlas IP whitelist includes Azure IPs
- Add `0.0.0.0/0` to whitelist for testing

### Issue: API Returns 401 Unauthorized
**Solution:**
- Verify `x-api-key` header is set in request
- Check `VARUTRI_API_KEY` environment variable is configured

### Issue: App Won't Start
**Solution:**
- Check logs: `az webapp log tail`
- Verify JAR file was deployed correctly
- Check Java version is 17

### Issue: Out of Memory
**Solution:**
- Upgrade to B1 plan: `az appservice plan update --sku B1 -g varutri-honeypot-rg -n varutri-honeypot-plan`
- B1 has 1.75GB RAM vs F1's 1GB

## Scaling Options 💪

When you need more performance:

### Basic Tier (B1) - $13/month
- [ ] Upgrade plan
  ```bash
  az appservice plan update \
    --name varutri-honeypot-plan \
    --resource-group varutri-honeypot-rg \
    --sku B1
  ```
- Better CPU and memory
- Always-on feature available
- Custom domains supported

### Standard Tier (S1) - $70/month
- Auto-scaling
- Staging slots
- Daily backups
- Best for production

## Clean Up (When Done) 🧹

- [ ] Delete all Azure resources
  ```bash
  az group delete --name varutri-honeypot-rg --yes --no-wait
  ```

- [ ] Verify deletion in Azure Portal
  - Visit: https://portal.azure.com
  - Check resource groups are removed

- [ ] Check remaining credits
  - Visit: https://www.microsoftazuresponsorships.com/

## Resources 📚

- [ ] Bookmark these links:
  - Azure Portal: https://portal.azure.com
  - Azure CLI Docs: https://docs.microsoft.com/cli/azure
  - Azure for Students: https://aka.ms/azureforstudents
  - Sponsorship Balance: https://www.microsoftazuresponsorships.com/
  - Spring Boot on Azure: https://docs.microsoft.com/azure/developer/java/spring-framework/

## Success! 🎉

Once all boxes are checked, your Varutri Honeypot is successfully deployed to Azure!

**Your App URLs:**
- Application: `https://your-app-name.azurewebsites.net`
- Health Check: `https://your-app-name.azurewebsites.net/actuator/health`
- API Endpoint: `https://your-app-name.azurewebsites.net/api/chat`

**Quick Commands:**
```bash
# View logs
az webapp log tail -g varutri-honeypot-rg -n your-app-name

# Restart
az webapp restart -g varutri-honeypot-rg -n your-app-name

# Stop (save credits)
az webapp stop -g varutri-honeypot-rg -n your-app-name

# Start
az webapp start -g varutri-honeypot-rg -n your-app-name

# Delete everything
az group delete -g varutri-honeypot-rg --yes --no-wait
```

---

**Need help?** Check [AZURE_SETUP.md](AZURE_SETUP.md) for detailed instructions or [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues.
