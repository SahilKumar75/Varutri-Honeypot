# Varutri Honeypot 🛡️

**Agentic Honeypot System for India AI Impact Buildathon**

An intelligent honeypot that engages with scammers using Ollama LLM (Llama 3), extracts threat intelligence, and reports findings to the GUVI Hackathon API.

## 🎯 Project Overview

Varutri is an AI-powered honeypot designed to:
- Engage scammers in realistic conversations using persona-driven AI
- Extract critical intelligence: UPI IDs, bank accounts, phishing URLs
- Provide real-time threat intelligence to security teams
- Meet strict buildathon requirements for API integration

## 🏗️ Architecture

- **Backend**: Spring Boot 3.2.2 (Java 17)
- **AI Engine**: Ollama with Llama 3 (8B)
- **Security**: API Key authentication
- **Intelligence**: Regex-based pattern extraction
- **Reporting**: REST callback to GUVI API

## 📋 API Endpoints

### POST `/api/chat`
Engage with the honeypot system.

**Headers:**
```
x-api-key: varutri_shield_2026
Content-Type: application/json
```

**Request Body:**
```json
{
  "sessionId": "unique-session-identifier",
  "message": "User message here",
  "conversationHistory": [
    {"role": "user", "content": "Previous message"},
    {"role": "assistant", "content": "Previous response"}
  ]
}
```

**Response:**
```json
{
  "status": "success",
  "reply": "AI-generated response"
}
```

## 🚀 Setup Instructions

### Prerequisites
1. **Java 17+** installed
2. **Maven 3.6+** installed
3. **Ollama** running locally with Llama 3

### Install Ollama (if not already installed)
```bash
# macOS
brew install ollama

# Start Ollama service
ollama serve

# Pull Llama 3 model
ollama pull llama3
```

### Verify Ollama is Running
```bash
curl -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3","prompt":"Hello","stream":false}'
```

### Run the Application
```bash
# Clone the repository
git clone https://github.com/SahilKumar75/Varutri-Honeypot.git
cd Varutri-Honeypot

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## 🧪 Testing

### Test with cURL
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "x-api-key: varutri_shield_2026" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session-1",
    "message": "Hi, I have an offer for you",
    "conversationHistory": []
  }'
```

### Run Unit Tests
```bash
mvn test
```

## 🌐 Public Deployment (ngrok)

For buildathon integration with GUVI Mock Scammer API:

```bash
# Install ngrok
brew install ngrok

# Create public tunnel
ngrok http 8080

# Use the generated https URL for the hackathon
```

## 🔍 Intelligence Extraction

Varutri automatically detects and extracts:

| Pattern | Regex | Example |
|---------|-------|---------|
| **UPI ID** | `[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}` | `user@paytm`, `9876543210@ybl` |
| **Bank Account** | Custom patterns | Account numbers with IFSC codes |
| **Phishing URLs** | `https?://[^\s]+` | Suspicious links |

## 👥 Team

- **Lead Developer**: [SahilKumar75](https://github.com/SahilKumar75)
- **Teammate**: _(To be added)_

## 🏆 Buildathon Success Metrics

1. **Engagement Duration**: Multi-turn conversations with scammers
2. **Intelligence Quality**: Successfully extracted UPI IDs, accounts, URLs
3. **System Stability**: Low latency (< 2s), 99% uptime
4. **API Compliance**: 100% adherence to GUVI API specifications

## 📝 Configuration

Edit `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080

# Ollama Configuration
ollama.base-url=http://localhost:11434
ollama.model=llama3

# Security
varutri.api-key=varutri_shield_2026

# Hackathon Callback
hackathon.callback-url=https://hackathon.guvi.in/api/updateHoneyPotFinalResult

# Logging
logging.level.com.varutri=DEBUG
```

## 🔒 Security Notes

- API key validation required for all requests
- Sessions tracked in-memory (consider Redis for production)
- Sensitive data sanitized before logging
- Final callback only triggered after session completion

## 📄 License

Built for India AI Impact Buildathon 2026

---

**Project Status**: 🚧 Active Development

**Last Updated**: January 31, 2026
