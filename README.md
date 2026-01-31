# Varutri Honeypot

Agentic Honeypot System for India AI Impact Buildathon

## Overview

An AI-powered honeypot that engages scammers in realistic conversations, extracts threat intelligence (UPI IDs, bank accounts, phishing URLs), and reports findings to the GUVI Hackathon API.

## Tech Stack

- Spring Boot 3.2.2 (Java 17)
- LLM: Hugging Face API (Llama 3.2) or Ollama (local)
- Security: Spring Security with API key validation
- Intelligence: Regex-based pattern extraction
- Build: Maven

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
x-api-key: varutri_shield_2026
Content-Type: application/json
```

**Request:**
```json
{
  "sessionId": "session-id",
  "message": "user message",
  "conversationHistory": []
}
```

**Response:**
```json
{
  "status": "success",
  "reply": "AI response"
}
```

### GET /health

Health check endpoint

## Intelligence Extraction

Automatically detects:
- UPI IDs: `user@paytm`, `9876543210@ybl`
- Bank accounts with IFSC codes
- Phishing URLs

## Deployment

Use ngrok for public access:
```bash
ngrok http 8080
```

## Configuration

Key settings in `application.properties`:
- `llm.provider`: `huggingface` or `ollama`
- `huggingface.api-key`: Your HF API key
- `varutri.api-key`: API key for requests (default: `varutri_shield_2026`)
- `hackathon.callback-url`: GUVI callback endpoint
- `varutri.session.max-turns`: Max conversation turns (default: 20)

## Team

- Lead Developer: SahilKumar75
- Teammate: TBD

## License

Built for India AI Impact Buildathon 2026
