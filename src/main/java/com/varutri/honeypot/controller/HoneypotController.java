package com.varutri.honeypot.controller;

import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.ChatResponse;
import com.varutri.honeypot.service.CallbackService;
import com.varutri.honeypot.service.IntelligenceExtractor;
import com.varutri.honeypot.service.OllamaService;
import com.varutri.honeypot.service.SessionStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class HoneypotController {

    private final OllamaService ollamaService;
    private final SessionStore sessionStore;
    private final IntelligenceExtractor intelligenceExtractor;
    private final CallbackService callbackService;

    @Value("${varutri.session.max-turns:20}")
    private int maxTurns;

    /**
     * Main chat endpoint
     * POST /api/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();

        log.info("📩 Received message for session {}: {}", sessionId,
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

        try {
            // Update session with incoming message
            sessionStore.addMessage(sessionId, "user", userMessage);

            // Get conversation history (merge with request history if provided)
            List<ChatRequest.ConversationMessage> conversationHistory = mergeConversationHistory(sessionId,
                    request.getConversationHistory());

            // Generate AI response using Ollama
            String aiResponse = ollamaService.generateResponse(userMessage, conversationHistory);

            // Update session with AI response
            sessionStore.addMessage(sessionId, "assistant", aiResponse);

            // Check if we should trigger final callback
            int turnCount = sessionStore.getTurnCount(sessionId);
            if (sessionStore.shouldTriggerCallback(sessionId, maxTurns)) {
                log.info("🎯 Session {} reached max turns ({}), triggering final callback", sessionId, turnCount);
                sendFinalCallback(sessionId);
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
     * Send final callback with extracted intelligence
     */
    private void sendFinalCallback(String sessionId) {
        try {
            // Get all messages from session
            List<String> allMessages = sessionStore.getAllMessages(sessionId);

            // Extract intelligence
            IntelligenceExtractor.IntelligenceData intelligence = intelligenceExtractor
                    .extractAllIntelligence(allMessages);

            // Get turn count
            int turnCount = sessionStore.getTurnCount(sessionId);

            // Send callback
            callbackService.sendFinalReport(
                    sessionId,
                    intelligence.upiIds(),
                    intelligence.bankAccounts(),
                    intelligence.phishingUrls(),
                    turnCount);

            // Clear session after callback
            sessionStore.clearSession(sessionId);

        } catch (Exception e) {
            log.error("Error sending final callback for session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}
