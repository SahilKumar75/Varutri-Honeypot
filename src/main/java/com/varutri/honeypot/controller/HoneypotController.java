package com.varutri.honeypot.controller;

import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.ChatResponse;
import com.varutri.honeypot.dto.ExtractedInfo;
import com.varutri.honeypot.service.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Main REST controller for honeypot chat API
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class HoneypotController {

    @Autowired(required = false)
    private OllamaService ollamaService;

    @Autowired(required = false)
    private HuggingFaceService huggingFaceService;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private CallbackService callbackService;

    @Autowired
    private InformationExtractor informationExtractor;

    @Autowired
    private ScamDetector scamDetector;

    @Autowired
    private EvidenceCollector evidenceCollector;

    @Autowired
    private GovernmentReportService governmentReportService;

    @Value("${varutri.session.max-turns:20}")
    private int maxTurns;

    /**
     * Main chat endpoint
     * POST /api/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();

        // Extract text from nested message object (GUVI format)
        String userMessage = request.getMessage().getText();
        String sender = request.getMessage().getSender();

        log.info("Received message for session {}: {} from {}", sessionId,
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage,
                sender);

        try {
            // Extract intelligence from user message
            ExtractedInfo extracted = informationExtractor.extractInformation(userMessage);

            // Detect scam type and threat level
            String scamType = scamDetector.detectScamType(userMessage);
            double threatLevel = scamDetector.calculateThreatLevel(userMessage);

            if (threatLevel >= 0.6) {
                log.warn("🚨 HIGH THREAT DETECTED! Session: {}, Type: {}, Level: {}",
                        sessionId, scamType, String.format("%.2f", threatLevel));
            }

            // Update session with incoming message
            sessionStore.addMessage(sessionId, "user", userMessage);

            // Get conversation history (merge with request history if provided)
            List<ChatRequest.ConversationMessage> conversationHistory = mergeConversationHistory(sessionId,
                    request.getConversationHistory());

            // Generate AI response using configured LLM provider
            String aiResponse = generateResponse(userMessage, conversationHistory);

            // Update session with AI response
            sessionStore.addMessage(sessionId, "assistant", aiResponse);

            // Collect evidence for this conversation turn
            evidenceCollector.collectEvidence(sessionId, userMessage, aiResponse);

            // Check if we should trigger final callback
            int turnCount = sessionStore.getTurnCount(sessionId);
            if (sessionStore.shouldTriggerCallback(sessionId, maxTurns)) {
                log.info("🎯 Session {} reached max turns ({}), triggering final callback", sessionId, turnCount);
                sendFinalCallback(sessionId);

                // Trigger automatic government report if high threat
                governmentReportService.processAutoReport(sessionId);
            }

            log.info("✅ Response generated for session {} (turn {})", sessionId, turnCount);

            return ResponseEntity.ok(ChatResponse.success(aiResponse));

        } catch (Exception e) {
            log.error("❌ Error processing chat for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(ChatResponse.error("Internal server error"));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"healthy\",\"service\":\"varutri-honeypot\"}");
    }

    /**
     * Manually trigger callback for a session (for testing)
     */
    @PostMapping("/callback/{sessionId}")
    public ResponseEntity<String> triggerCallback(@PathVariable String sessionId) {
        log.info("Manual callback triggered for session: {}", sessionId);
        sendFinalCallback(sessionId);
        return ResponseEntity.ok("{\"status\":\"callback_sent\"}");
    }

    /**
     * Merge conversation histories
     */
    private List<ChatRequest.ConversationMessage> mergeConversationHistory(
            String sessionId,
            List<ChatRequest.ConversationMessage> requestHistory) {
        // Use request history if provided, otherwise use stored session history
        if (requestHistory != null && !requestHistory.isEmpty()) {
            return requestHistory;
        }
        return sessionStore.getConversationHistory(sessionId);
    }

    /**
     * Generate response using configured LLM provider
     */
    private String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory) {
        if (huggingFaceService != null) {
            return huggingFaceService.generateResponse(userMessage, conversationHistory);
        } else if (ollamaService != null) {
            return ollamaService.generateResponse(userMessage, conversationHistory);
        } else {
            throw new IllegalStateException(
                    "No LLM service configured. Please set llm.provider to 'ollama' or 'huggingface'");
        }
    }

    /**
     * Send final callback with extracted intelligence to GUVI
     */
    private void sendFinalCallback(String sessionId) {
        try {
            // Get evidence package
            EvidenceCollector.EvidencePackage evidence = evidenceCollector.getEvidence(sessionId);

            if (evidence != null) {
                // Generate agent notes
                String agentNotes = String.format("Scam type: %s, Threat level: %.2f, Engagement successful",
                        evidence.getScamType(), evidence.getThreatLevel());

                // Send to GUVI
                callbackService.sendFinalReport(
                        sessionId,
                        evidence.getExtractedInfo(),
                        evidence.getConversation().size(),
                        agentNotes);

                log.info("Final callback sent to GUVI for session {}", sessionId);
            } else {
                log.warn("No evidence found for session {}, skipping callback", sessionId);
            }

            // Clear session after callback
            sessionStore.clearSession(sessionId);

        } catch (Exception e) {
            log.error("Error sending final callback for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Get evidence for a specific session
     * GET /api/evidence/{sessionId}
     */
    @GetMapping("/evidence/{sessionId}")
    public ResponseEntity<?> getEvidence(@PathVariable String sessionId) {
        log.info("📊 Evidence requested for session: {}", sessionId);

        EvidenceCollector.EvidencePackage evidence = evidenceCollector.getEvidence(sessionId);

        if (evidence == null) {
            return ResponseEntity.status(404)
                    .body("{\"error\":\"No evidence found for session: " + sessionId + "\"}");
        }

        return ResponseEntity.ok(evidence);
    }

    /**
     * Get all high-threat evidence packages
     * GET /api/evidence/high-threat
     */
    @GetMapping("/evidence/high-threat")
    public ResponseEntity<?> getHighThreatEvidence() {
        log.info("🚨 High-threat evidence requested");
        List<EvidenceCollector.EvidencePackage> evidence = evidenceCollector.getHighThreatEvidence();
        return ResponseEntity.ok(evidence);
    }

    /**
     * Get all evidence packages
     * GET /api/evidence
     */
    @GetMapping("/evidence")
    public ResponseEntity<?> getAllEvidence() {
        log.info("📊 All evidence requested");
        List<EvidenceCollector.EvidencePackage> evidence = evidenceCollector.getAllEvidence();
        return ResponseEntity.ok(evidence);
    }
}
