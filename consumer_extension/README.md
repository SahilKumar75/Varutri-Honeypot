# Varutri Scam Shield Extension (Starter)

Browser extension starter for consumer-side phishing and suspicious content triage.

## Included Flows

1. Configure backend connection (`baseUrl`, `appId`, `deviceId`, `appVersion`)
2. Issue consumer token via `/api/consumer/auth/token`
3. Analyze active tab selection or URL via `/api/consumer/analyze`
4. Pull capability matrix and history

## Load in Chrome

1. Open `chrome://extensions`
2. Enable **Developer mode**
3. Click **Load unpacked**
4. Select the `consumer_extension` folder

## Local Backend Assumption

Default API URL is `http://localhost:8080`.

If backend runs elsewhere, update Base URL in popup settings.

## Security Notes

- Uses `chrome.storage.local` for local config/token storage.
- This is a starter and not hardened for production secrets.
- For production rollout, replace token storage with secure handling, tighten host permissions, and add CSP hardening/review.
