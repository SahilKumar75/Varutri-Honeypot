package com.varutri.honeypot.controller;

import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.ChatResponse;
import com.varutri.honeypot.dto.WhatsAppWebhook;
import com.varutri.honeypot.service.HuggingFaceService;
import com.varutri.honeypot.service.OllamaService;
import com.varutri.honeypot.service.SessionStore;
import com.varutri.honeypot.service.WhatsAppService;
import com.varutri.honeypot.service.EvidenceCollector;
import com.varutri.honeypot.service.InformationExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for WhatsApp webhook integration
 */
@Slf4j
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private HuggingFaceService huggingFaceService;

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private EvidenceCollector evidenceCollector;

    @Autowired
    private InformationExtractor informationExtractor;

    @Value("${whatsapp.verify.token:varutri_webhook_2026}")
    private String verifyToken;

    @Value("${llm.provider:huggingface}")
    private String llmProvider;

    // Map to track WhatsApp phone numbers to session IDs
    private final Map<String, String> phoneToSession = new HashMap<>();

    /**
     * Webhook verification endpoint (required by Meta)
     */
    @GetMapping("/webhook")
    public ResponseEntity<?> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        log.info("Webhook verification request: mode={}, token={}", mode, token);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("✅ Webhook verified successfully");
            return ResponseEntity.ok(challenge);
        } else {
            log.warn("❌ Webhook verification failed");
            return ResponseEntity.status(403).body("Verification failed");
        }
    }

    /**
     * Webhook endpoint for receiving WhatsApp messages
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody WhatsAppWebhook webhook) {
        log.info("Received WhatsApp webhook: {}", webhook.getObject());

        try {
            if (webhook.getEntry() == null || webhook.getEntry().isEmpty()) {
                return ResponseEntity.ok("No entries");
            }

            for (WhatsAppWebhook.Entry entry : webhook.getEntry()) {
                if (entry.getChanges() == null)
                    continue;

                for (WhatsAppWebhook.Change change : entry.getChanges()) {
                    if (change.getValue() == null || change.getValue().getMessages() == null)
                        continue;

                    for (WhatsAppWebhook.Message message : change.getValue().getMessages()) {
                        handleIncomingMessage(message);
                    }
                }
            }

            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok("ERROR");
        }
    }

    /**
     * Handle incoming WhatsApp message
     */
    private void handleIncomingMessage(WhatsAppWebhook.Message message) {
        String from = message.getFrom();
        String messageText = null;
        String buttonPayload = null;

        // Extract message text
        if (message.getText() != null) {
            messageText = message.getText().getBody();
        } else if (message.getButton() != null) {
            buttonPayload = message.getButton().getPayload();
            messageText = message.getButton().getText();
        }

        if (messageText == null) {
            log.warn("No text in message from {}", from);
            return;
        }

        log.info("📱 WhatsApp message from {}: {}", from, messageText);

        // Check if this is a "Report to Varutri" button click
        if ("REPORT_SCAM".equals(buttonPayload)) {
            handleScamReport(from, messageText);
            return;
        }

        // Get or create session for this phone number
        String sessionId = phoneToSession.computeIfAbsent(from,
                phone -> "wa-" + phone + "-" + System.currentTimeMillis());

        // Forward to honeypot
        ChatResponse response = processScamMessage(sessionId, from, messageText);

        // Send response back to scammer via WhatsApp
        if (response != null && response.getReply() != null) {
            whatsAppService.sendMessage(from, response.getReply());
        }
    }

    /**
     * Handle scam report from user
     */
    private void handleScamReport(String userPhone, String scamMessage) {
        log.info("🚨 User {} reported scam message: {}", userPhone, scamMessage);

        // Create session
        String sessionId = "wa-user-" + userPhone + "-" + System.currentTimeMillis();
        phoneToSession.put(userPhone, sessionId);

        // Notify user that Varutri is taking over
        whatsAppService.notifyUserTakeover(userPhone, sessionId);

        // Send confirmation button
        whatsAppService.sendButtonMessage(
                userPhone,
                "I'll handle this scammer for you. Forward their messages to me, and I'll extract intelligence.",
                "Got it!",
                "CONFIRM_TAKEOVER");
    }

    /**
     * Process scam message through honeypot
     */
    private ChatResponse processScamMessage(String sessionId, String from, String messageText) {
        try {
            // Build chat request
            ChatRequest request = new ChatRequest();
            request.setSessionId(sessionId);

            ChatRequest.Message msg = new ChatRequest.Message();
            msg.setSender("scammer");
            msg.setText(messageText);
            msg.setTimestamp(LocalDateTime.now().toString());
            request.setMessage(msg);

            request.setConversationHistory(new ArrayList<>());
            request.setMetadata(new ChatRequest.Metadata());

            // Get AI response
            String aiResponse;
            if ("ollama".equalsIgnoreCase(llmProvider)) {
                aiResponse = ollamaService.generateResponse(messageText, request.getConversationHistory(), "UNKNOWN",
                        0.5);
            } else {
                aiResponse = huggingFaceService.generateResponse(messageText, request.getConversationHistory(),
                        "UNKNOWN", 0.5);
            }

            // Store conversation
            sessionStore.addMessage(sessionId, "scammer", messageText);
            sessionStore.addMessage(sessionId, "assistant", aiResponse);

            // Extract intelligence
            var intelligence = informationExtractor.extractInformation(messageText);
            evidenceCollector.collectEvidence(sessionId, messageText, aiResponse);

            // Notify user if significant intelligence found
            if (!intelligence.getUpiIds().isEmpty()) {
                whatsAppService.notifyIntelligenceExtracted(from, "UPI ID",
                        String.join(", ", intelligence.getUpiIds()));
            }
            if (!intelligence.getBankAccountNumbers().isEmpty()) {
                whatsAppService.notifyIntelligenceExtracted(from, "Bank Account",
                        String.join(", ", intelligence.getBankAccountNumbers()));
            }

            return new ChatResponse("success", aiResponse);

        } catch (Exception e) {
            log.error("Error processing scam message: {}", e.getMessage(), e);
            return new ChatResponse("error", "I'm experiencing technical difficulties.");
        }
    }

    /**
     * Manual endpoint to initiate takeover
     */
    @PostMapping("/takeover")
    public ResponseEntity<?> initiateTakeover(@RequestBody Map<String, String> request) {
        String userPhone = request.get("phone");
        String scamMessage = request.get("message");

        if (userPhone == null || scamMessage == null) {
            return ResponseEntity.badRequest().body("Missing phone or message");
        }

        handleScamReport(userPhone, scamMessage);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Varutri takeover initiated"));
    }
}
