# Consumer Protection Architecture (HLD + LLD)

This document defines management structure, high-level design, low-level design, flow handling, state handling, and caching strategy for the consumer-facing anti-scam product.

## 1) Product Scope

Channels in MVP:
- SMS
- CALL
- WHATSAPP
- EMAIL
- BROWSER
- PROMPT (developer-risk prompt analysis)

Client surfaces:
- Flutter mobile app shell (`consumer_app`)
- Browser extension shell (`consumer_extension`)

Server surface:
- Spring Boot consumer APIs under `/api/consumer/**`

## 2) HLD (High-Level Design)

```text
+-----------------------+      +-----------------------+
| Flutter Consumer App  |      | Browser Extension     |
| - capture/share flow  |      | - active tab analysis |
| - risk presence UI    |      | - page risk banner    |
+-----------+-----------+      +-----------+-----------+
            |                              |
            | Bearer token + payload       | Bearer token + payload
            +---------------+--------------+
                            |
                            v
                 +--------------------------+
                 | Spring Boot Consumer API |
                 | /auth /analyze /history  |
                 | /capabilities            |
                 +------------+-------------+
                              |
           +------------------+-------------------+
           |                                      |
           v                                      v
+------------------------+              +------------------------+
| Threat + Extraction    |              | Session + Evidence DB  |
| ensemble scorer        |              | Mongo repositories     |
| info extractor         |              | history and detail     |
+------------------------+              +------------------------+
```

## 3) LLD (Low-Level Design)

### 3.1 Server Components

- `ConsumerAuthController`
  - Issues short-lived bearer tokens for allowed app IDs.
- `ConsumerTokenFilter`
  - Enforces token auth for `/api/consumer/**` except token issuance.
- `ConsumerController`
  - `POST /analyze`
  - `GET /history`
  - `GET /history/{sessionId}`
  - `GET /capabilities?platform=...`
- `ConsumerSignalService`
  - Builds analyzable text from channel payload.
  - Runs scoring + extraction.
  - Persists session/evidence.
  - Invalidates history cache after writes.
- `ConsumerHistoryService`
  - Builds list/detail views from session/evidence entities.
  - Uses cache wrapper for low-latency timeline fetch.
- `ConsumerCapabilitiesService`
  - Returns platform-specific channel automation matrix.
- `ConsumerCacheService`
  - TTL cache for history list/detail and capabilities.

### 3.2 Client Components (Flutter)

- `ConsumerAppState`
  - Single source of truth for:
    - token lifecycle
    - latest analysis
    - history list/detail
    - capability matrix
    - operation status
  - Client-side TTL caching for:
    - history list
    - history detail
    - capability matrix
- `ConsumerHomePage`
  - Flow-oriented tabs:
    - Analyze
    - History
    - Capabilities
  - Connection/token boundary panel
  - On-screen risk-presence banner

### 3.3 Client Components (Extension)

- `background.js`
  - Message router for popup actions.
  - Token issuance and protected API calls.
  - Active tab context capture and analysis call.
- `content.js`
  - Selection/URL capture helper.
  - In-page transient risk banner.
- `popup.js`
  - Management panel for config + action flow.

## 4) Flow Structures

### 4.1 Flow A: Token Provisioning

1. Client collects base URL + app identity.
2. Calls `POST /api/consumer/auth/token`.
3. Stores token and expiry in memory/local storage.
4. UI moves to `Connected` operational state.

### 4.2 Flow B: Analyze Suspicious Signal

1. User shares/pastes suspicious content.
2. Client builds channel payload and metadata.
3. Server scores threat + extracts indicators.
4. Server persists timeline + evidence.
5. Server invalidates history cache keys.
6. Client updates on-screen risk presence.

### 4.3 Flow C: Timeline Investigation

1. Client requests recent history.
2. Server serves from TTL cache when valid.
3. User drills down into one session detail.
4. Client can review extracted indicators for reporting action.

### 4.4 Flow D: Capability-Driven UX

1. Client requests `/capabilities?platform=...`.
2. Server returns matrix of automation level and limitations.
3. Client adapts UI flow to manual/partial automation constraints.

## 5) Caching Strategy

### 5.1 Server-Side Cache

Current implementation (in-memory TTL):
- History list TTL: `consumer.cache.history.ttl-seconds` (default 45)
- History detail TTL: same history TTL bucket
- Capability TTL: `consumer.cache.capabilities.ttl-seconds` (default 300)

Invalidation policy:
- On new analysis write, clear history list cache and matching session detail key.

Production recommendation:
- Move to Redis for horizontal scale.
- Add cache metrics (`hit`, `miss`, `eviction`) and alerts.

### 5.2 Client-Side Cache

Flutter state cache:
- History list: 30s TTL
- Session detail: 2m TTL
- Capabilities: 5m TTL

Extension storage:
- Keeps config + token + latest analysis in local storage.

## 6) State Management

State model uses deterministic transitions:
- `Idle`
- `TokenRequested`
- `TokenReady`
- `Analyzing`
- `AnalysisReady`
- `HistoryLoading`
- `CapabilitiesLoading`
- `Error`

On-screen presence is always derived from latest threat output:
- `Idle`: no recent analysis
- `Safe`: low/no risk signals
- `Watch`: medium/suspicious signals
- `Danger`: high/critical threat

## 7) Server Handling and Security

- Token boundary for consumer routes (separate from x-api-key route security).
- Allowed app IDs via property: `consumer.auth.allowed-app-ids`.
- Rate limiting remains active globally.
- Input sanitization and prompt-injection checks run before analysis.

## 8) Operational Management Plan

Release tracks:
1. Track A (complete): Consumer API + token + history + capability matrix.
2. Track B (in progress): Rich client flows + extension shell + risk presence.
3. Track C: Native capture modules and provider integrations (SMS roles, Call screening, OAuth email).

Runbook checks per release:
- API token issuance smoke test
- Analyze endpoint regression test
- Cache invalidation test after analyze writes
- History detail consistency test
- Capability matrix contract test per platform value

SLO targets (initial):
- Token issuance p95 < 300ms
- History list p95 < 200ms (cache hit path)
- Analyze request p95 < 2.5s

## 9) Next Engineering Steps

- Add Redis-backed cache implementation behind `ConsumerCacheService` interface.
- Add telemetry: request IDs, cache metrics, per-channel latency.
- Introduce persistent risk feed stream for real-time app/extension presence.
- Add CI checks for extension lint + Flutter static analysis once toolchains are available in CI image.
