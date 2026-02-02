# Render Deployment Guide

## Quick Status Check

Your backend is **currently working** on Render:
- URL: https://varutri-honeypot.onrender.com
- Status: ONLINE ✅
- Last test: AI responded with "kya hai beta... who is this"

## Using Render CLI

### 1. Install Render CLI (Already Done!)
```bash
brew install render
```

### 2. Authenticate with Render
```bash
render login
```

This will open your browser to authenticate with your Render account.

### 3. List Your Services
```bash
render services list
```

### 4. Trigger Manual Deploy
```bash
# Find your service ID first
render services list

# Then deploy (replace SERVICE_ID with your actual service ID)
render deploy --service=SERVICE_ID
```

### 5. View Logs
```bash
render logs --service=SERVICE_ID --tail
```

## Alternative: Deploy from Render Dashboard

1. Go to https://dashboard.render.com
2. Click on your `varutri-honeypot` service
3. Click "Manual Deploy" button
4. Select "Deploy latest commit"
5. Wait for build to complete

## Troubleshooting

### If deployment fails:

1. **Check build logs** on Render dashboard
2. **Common issues:**
   - Java version mismatch (should be Java 17)
   - Maven build errors
   - Missing environment variables

### Current Configuration:
- Java Version: 17
- Build Command: `mvn clean install`
- Start Command: `java -jar target/honeypot-1.0.0.jar`

## Test Your Deployment

After deploying, test with:

```bash
curl -X POST https://varutri-honeypot.onrender.com/api/chat \
  -H "Content-Type: application/json" \
  -H "x-api-key: varutri_shield_2026" \
  -d '{
    "sessionId": "test-001",
    "message": {
      "sender": "scammer",
      "text": "Hello! You won 10 lakh rupees!",
      "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")'"
    }
  }'
```

Expected response:
```json
{
  "status": "success",
  "reply": "Kya... how I won this? I dont beleive..."
}
```

## Environment Variables on Render

Make sure these are set in your Render dashboard:

- `GEMINI_API_KEY`: Your Google Gemini API key
- `API_KEY`: `varutri_shield_2026`
- `PORT`: `8080` (Render sets this automatically)

## Next Steps

1. Authenticate: `render login`
2. Check services: `render services list`
3. Deploy if needed: `render deploy --service=<your-service-id>`
4. Monitor logs: `render logs --service=<your-service-id> --tail`
