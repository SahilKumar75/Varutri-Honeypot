# Varutri Honeypot - Quick Start Guide

## Current Status

Application successfully built and running on `http://localhost:8080`

## Running the Application

```bash
# Set Java 17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

# Build
mvn clean package -DskipTests

# Run
java -jar target/honeypot-1.0.0.jar
```

## Testing the API

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "x-api-key: varutri_shield_2026" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-001",
    "message": "Hello",
    "conversationHistory": []
  }'
```

Consumer token + analysis endpoint example:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/consumer/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "varutri-mobile",
    "deviceId": "android-test-device-001",
    "platform": "ANDROID",
    "appVersion": "1.0.0"
  }' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

curl -X POST http://localhost:8080/api/consumer/analyze \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "channel": "WHATSAPP",
    "payload": {
      "text": "Urgent KYC update required, share OTP now",
      "senderId": "+919000000001"
    },
    "metadata": {
      "platform": "ANDROID",
      "sourceApp": "whatsapp",
      "locale": "en_IN"
    }
  }'

curl -X GET "http://localhost:8080/api/consumer/history?limit=10" \
  -H "Authorization: Bearer ${TOKEN}"

curl -X GET "http://localhost:8080/api/consumer/capabilities?platform=ANDROID" \
  -H "Authorization: Bearer ${TOKEN}"
```

## Configuration

Edit `src/main/resources/application.properties`:

- `llm.provider`: `huggingface` or `ollama`
- `huggingface.api-key`: Your HF API key (currently set)
- `varutri.api-key`: API key for requests (`varutri_shield_2026`)

## Deployment

Use ngrok for public access:
```bash
ngrok http 8080
```

Provide the ngrok HTTPS URL to GUVI platform.

## Notes

- API key authentication: Working
- Hugging Face integration: Configured (may have rate limits/cold start delays)
- All code pushed to: https://github.com/SahilKumar75/Varutri-Honeypot
- Consumer app shell: `consumer_app/`
- Browser extension shell: `consumer_extension/`
- HLD/LLD + flow architecture doc: `CONSUMER_ARCHITECTURE.md`
