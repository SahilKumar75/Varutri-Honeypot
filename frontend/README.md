# Varutri Consumer Command Center (Web)

This frontend is now a consumer-facing command center wired to the real consumer APIs.

## Flows Included

1. Server boundary and token issuance
   - `POST /api/consumer/auth/token`
2. Suspicious signal analysis
   - `POST /api/consumer/analyze`
3. Evidence timeline list and detail drill-down
   - `GET /api/consumer/history`
   - `GET /api/consumer/history/{sessionId}`
4. Platform capability matrix and constraints
   - `GET /api/consumer/capabilities?platform=...`

## UX Highlights

- On-screen risk presence states: Idle, Safe, Watch, Danger
- Channel-aware analysis input (`SMS`, `CALL`, `WHATSAPP`, `EMAIL`, `BROWSER`, `MANUAL`, `PROMPT`)
- Client-side TTL cache for history, detail, and capabilities
- Responsive layout for desktop and mobile

## Local Run

1. Start backend (`mvn spring-boot:run`)
2. Open frontend:

```bash
cd frontend
open index.html
```

3. In the UI:
   - Confirm Base URL (`http://localhost:8080`)
   - Click **Issue Token**
   - Run analysis and inspect timeline/capabilities

## Notes

- This is a production-oriented integration surface for end-to-end flow testing.
- Browser CORS/security policies still apply when opening from file protocol; if needed, host the folder via a lightweight static server.
