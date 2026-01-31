# ⚠️ IMPORTANT: Building This Project

## Lombok Requirement

This project uses **Lombok** to reduce boilerplate code. Maven command-line builds currently have an annotation processing configuration issue.

### ✅ **Recommended: Use IntelliJ IDEA**

1. **Install Lombok Plugin**:
   - IntelliJ IDEA → Settings → Plugins → Search "Lombok" → Install
   - Restart IDE

2. **Enable Annotation Processing**:
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - Check "Enable annotation processing"
   - Click Apply

3. **Open Project**:
   - File → Open → Select `/Desktop/Varutri-Honeypot`
   - Wait for Maven import to complete
   - Build → Build Project (Ctrl+F9)

4. **Run**:
   - Right-click `VarutriHoneypotApplication.java`
   - Run 'VarutriHoneypotApplication'

### 🔧 Alternative: Eclipse

1. Install "Lombok" from https://projectlombok.org/download
2. Run the lombok.jar installer
3. Select your Eclipse installation
4. Restart Eclipse
5. Import as Maven project

### ⚡ Quick Test

Once built in IDE:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "x-api-key: varutri_shield_2026" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-001",
    "message": "Hello!",
    "conversationHistory": []
  }'
```

---

**For full documentation, see [README.md](README.md)**
