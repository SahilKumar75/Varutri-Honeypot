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

### GET /health

Health check endpoint

## Intelligence Extraction

Automatically detects:
- UPI IDs: `user@paytm`, `9876543210@ybl`
- Bank accounts with IFSC codes
- Phishing URLs

### Cloud Deployment
Deploy to AWS or Azure with $100 free credit:
See [AZURE_QUICKSTART.md](AZURE_QUICKSTART.md) for details.

#### Render
Already configured - push to GitHub and connect Render.

## Configuration

Key settings in `application.properties`:
- `llm.provider`: `huggingface` or `ollama`
- `huggingface.api-key`: Your HF API key
- `varutri.api-key`: API key for requests
- `hackathon.callback-url`: GUVI callback endpoint
- `varutri.session.max-turns`: Max conversation turns (default: 40)
