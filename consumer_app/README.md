# Varutri Consumer App Shell

This is a starter Flutter shell for consumer-side scam reporting and analysis.

## What it supports

- Token exchange: POST /api/consumer/auth/token
- Consumer analysis: POST /api/consumer/analyze
- Consumer history: GET /api/consumer/history

## Run locally

1. Install Flutter SDK (stable channel)
2. From this folder:

```bash
flutter pub get
flutter run
```

## Default API URL

The app starts with `http://10.0.2.2:8080` (Android emulator host mapping).
Change Base URL in the UI when needed.

## Notes

- This is an integration shell, not a production UI.
- iOS/Android platform-specific capture modules should be added in later iterations.
