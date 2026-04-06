#!/bin/bash

# Simple test script for Varutri Honeypot API

echo "Testing Varutri Honeypot API..."
echo ""

# Test 1: Health check
echo "1. Testing health endpoint..."
curl -s http://localhost:8080/api/health
echo -e "\n"

# Test 2: Chat API
echo "2. Testing chat endpoint..."
curl -X POST http://localhost:8080/api/chat \
  -H "x-api-key: varutri_shield_2026" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session",
    "message": "Hello",
    "conversationHistory": []
  }'
echo -e "\n"

# Test 3: Consumer Analysis API
echo "3. Testing consumer analysis endpoint..."

TOKEN=$(curl -s -X POST http://localhost:8080/api/consumer/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "varutri-mobile",
    "deviceId": "android-local-test-001",
    "platform": "ANDROID",
    "appVersion": "1.0.0"
  }' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "[ERROR] Failed to fetch consumer access token"
  exit 1
fi

ANALYZE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/consumer/analyze \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "channel": "SMS",
    "payload": {
      "text": "Congratulations! Claim your lottery by sending 5000 to prize@paytm",
      "senderId": "+919876543210",
      "url": "http://fake-claim-now.example"
    },
    "metadata": {
      "platform": "ANDROID",
      "sourceApp": "messages",
      "locale": "en_IN"
    }
  }')

echo "$ANALYZE_RESPONSE"
echo -e "\n"

SESSION_ID=$(echo "$ANALYZE_RESPONSE" | sed -n 's/.*"sessionId":"\([^"]*\)".*/\1/p')
if [ -z "$SESSION_ID" ]; then
  echo "[WARN] Could not parse sessionId from analyze response"
fi
echo -e "\n"

# Test 4: Consumer History API
echo "4. Testing consumer history endpoint..."
curl -s "http://localhost:8080/api/consumer/history?limit=5" \
  -H "Authorization: Bearer ${TOKEN}"
echo -e "\n"

# Test 5: Consumer Capabilities API
echo "5. Testing consumer capabilities endpoint..."
curl -s "http://localhost:8080/api/consumer/capabilities?platform=ANDROID" \
  -H "Authorization: Bearer ${TOKEN}"
echo -e "\n"

# Test 6: Consumer History Detail API
echo "6. Testing consumer history detail endpoint..."
if [ -n "$SESSION_ID" ]; then
  curl -s "http://localhost:8080/api/consumer/history/${SESSION_ID}" \
    -H "Authorization: Bearer ${TOKEN}"
else
  echo "[SKIP] sessionId unavailable"
fi
echo -e "\n"

echo "Test complete!"
