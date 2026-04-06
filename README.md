# Varutri Honeypot

## Overview

An AI-powered honeypot system that engages scammers in realistic conversations using a human persona, automatically extracts threat intelligence (UPI IDs, bank accounts, phone numbers, phishing URLs), detects scam patterns, and collects evidence for law enforcement.

### Key Features

**Agentic AI Engagement** - LLM-powered realistic persona (Rajesh Kumar, 67-year-old retired teacher)  
**Intelligence Extraction** - Automatic extraction of UPI IDs, bank details, phone numbers, URLs  
**Scam Detection** - Pattern-based detection of investment, lottery, phishing, and job scams  
**Threat Assessment** - Real-time threat level calculation (0.0-1.0)  
**Evidence Collection** - Structured storage of conversations and extracted intelligence  
**API Integration** - RESTful API for external systems and law enforcement

## Tech Stack

- **Backend:** Spring Boot 3.2.2 (Java 17)
- **LLM:** Hugging Face API (Llama 3.3 70B Instruct)
- **Security:** Spring Security with API key validation
- **Intelligence:** Regex-based pattern extraction + keyword detection
- **Build:** Maven

## Quick Start

### Prerequisites

- Java 17+
- Maven
- Hugging Face API key (get from https://huggingface.co/settings/tokens)

### Setup

1. Clone repository
```bash
git clone https://github.com/SahilKumar75/Varutri-Honeypot.git
cd Varutri-Honeypot
```

2. Configure API key in `src/main/resources/application.properties`
```properties
llm.provider=huggingface
huggingface.api-key=YOUR_API_KEY_HERE
```

3. Build and run
```bash
mvn clean install
mvn spring-boot:run
```

Application starts on `http://localhost:8080`

## API Endpoints

### POST /api/chat

**Headers:**
```
x-api-key: Your_api_key
Content-Type: application/json
```

### POST /api/consumer/analyze

Consumer-facing suspicious content analysis endpoint for mobile apps, browser extensions, and share flows.

First get a consumer bearer token:

```json
POST /api/consumer/auth/token
{
	"appId": "varutri-mobile",
	"deviceId": "device-abc-123",
	"platform": "ANDROID",
	"appVersion": "1.0.0"
}
```

Then call consumer endpoints using:

```
Authorization: Bearer <accessToken>
```

Example request:
```json
{
	"channel": "SMS",
	"payload": {
		"text": "You won 25 lakh. Send 5000 to claim now.",
		"senderId": "+919876543210",
		"url": "http://claim-prize-now.example"
	},
	"metadata": {
		"platform": "ANDROID",
		"sourceApp": "messages",
		"locale": "en_IN"
	}
}
```

### GET /api/consumer/history

List recent consumer analyses.

### GET /api/consumer/history/{sessionId}

Get detailed timeline and extracted indicators for one consumer session.

### GET /api/consumer/capabilities?platform=ANDROID

Return platform-aware channel automation limits and recommended flows.

### GET /api/health

Health check endpoint

## Intelligence Extraction

Automatically detects:
- UPI IDs: `user@paytm`, `9876543210@ybl`
- Bank accounts with IFSC codes
- Phishing URLs

### Cloud Deployment
Deploy to AWS or Azure with $100 free credit:
See [AZURE_QUICKSTART.md](AZURE_QUICKSTART.md) for details.

## Consumer Clients

- Flutter consumer shell: [consumer_app/README.md](consumer_app/README.md)
- Browser extension shell: [consumer_extension/README.md](consumer_extension/README.md)
- HLD/LLD and flow architecture: [CONSUMER_ARCHITECTURE.md](CONSUMER_ARCHITECTURE.md)

#### Render
Already configured - push to GitHub and connect Render.

## Configuration

Key settings in `application.properties`:
- `llm.provider`: `huggingface` or `ollama`
- `huggingface.api-key`: Your HF API key
- `varutri.api-key`: API key for requests
- `hackathon.callback-url`: GUVI callback endpoint
- `varutri.session.max-turns`: Max conversation turns (default: 40)
