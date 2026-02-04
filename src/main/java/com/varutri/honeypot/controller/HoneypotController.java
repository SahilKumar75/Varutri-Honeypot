package com.varutri.honeypot.controller;

import com.varutri.honeypot.dto.ApiResponse;
import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.ChatResponse;
import com.varutri.honeypot.dto.ExtractedInfo;
import com.varutri.honeypot.dto.ThreatAssessmentResponse;
import com.varutri.honeypot.exception.ResourceNotFoundException;
import com.varutri.honeypot.service.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Main REST controller for honeypot chat API
 * Returns standardized ApiResponse objects with proper HTTP status codes
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
    private EvidenceCollector evidenceCollector;

    @Autowired
    private GovernmentReportService governmentReportService;

    @Autowired
    private InputSanitizer inputSanitizer;

    @Autowired
    private EnsembleThreatScorer ensembleThreatScorer;

    @Value("${varutri.session.max-turns:20}")
    private int maxTurns;

    @Value("${llm.provider:huggingface}")
    private String llmProvider;

    /**
     * Main chat endpoint
     * POST /api/chat
     * 
     * @return 200 OK with chat response on success
     * @return 400 Bad Request on validation errors
     * @return 503 Service Unavailable if LLM fails
     */
    @PostMapping("/chat")
    /**
     * Main chat endpoint (Asynchronous)
     * POST /api/chat
     * 
     * @return 200 OK with chat response on success
     * @return 400 Bad Request on validation errors
     * @return 503 Service Unavailable if LLM fails
     */
    @PostMapping("/chat")
    public java.util.concurrent.CompletableFuture<ResponseEntity<ApiResponse<ChatResponse>>> chat(
            @Valid @RequestBody ChatRequest request) {

        long startTime = System.currentTimeMillis();
        String sessionId = inputSanitizer.sanitizeSessionId(request.getSessionId());
        String userMessage = request.getMessage().getText();
        String sender = request.getMessage().getSender();

        log.info("Received message for session {}: {} from {}",
                sessionId, inputSanitizer.sanitizeForLogging(userMessage), sender);

        if (inputSanitizer.containsPromptInjection(userMessage)) {
            log.warn("Potential prompt injection detected in session {}", sessionId);
        }

        // 1. Parallel Task: Extract Information (runs in common pool)
        java.util.concurrent.CompletableFuture<ExtractedInfo> extractionFuture = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> informationExtractor.extractInformation(userMessage));

        // 2. Parallel Task: Threat Assessment (fully async pipeline)
        java.util.concurrent.CompletableFuture<EnsembleThreatScorer.ThreatAssessment> assessmentFuture = ensembleThreatScorer
                .assessThreatAsync(userMessage, request.getConversationHistory());

        // 3. Combine results and proceed to Response Generation
        return java.util.concurrent.CompletableFuture.allOf(extractionFuture, assessmentFuture)
                .thenCompose(v -> {
                    // Both parallel tasks are done
                    ExtractedInfo extracted = extractionFuture.join();
                    EnsembleThreatScorer.ThreatAssessment assessment = assessmentFuture.join();

                    double threatLevel = assessment.ensembleScore;
                    String scamType = assessment.primaryScamType;
                    String threatCategory = assessment.threatLevel;

                    if (assessment.isHighThreat()) {
                        log.warn(
                                "HIGH THREAT DETECTED! Session: {}, Type: {}, Level: {}, Confidence: {}%, Layers: {}/5",
                                sessionId, scamType, threatCategory,
                                String.format("%.0f", assessment.calibratedConfidence * 100),
                                assessment.triggeredLayers);
                    }

                    // Synchronous DB operations (keep fast enough, or move to separate thread if
                    // needed)
                    sessionStore.addMessage(sessionId, "user", userMessage);
                    List<ChatRequest.ConversationMessage> conversationHistory = mergeConversationHistory(sessionId,
                            request.getConversationHistory());

                    // 4. Async Response Generation
                    return generateResponseAsync(userMessage, conversationHistory, scamType, threatLevel)
                            .thenApply(aiResponse -> {
                                // 5. Post-response processing
                                sessionStore.addMessage(sessionId, "assistant", aiResponse);
                                evidenceCollector.collectEvidence(sessionId, userMessage, aiResponse);

                                int turnCount = sessionStore.getTurnCount(sessionId);
                                if (sessionStore.shouldTriggerCallback(sessionId, maxTurns)) {
                                    log.info("Session {} reached max turns, triggering callback", sessionId);
                                    // Run non-critical callbacks in background
                                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                                        sendFinalCallback(sessionId);
                                        governmentReportService.processAutoReport(sessionId);
                                    });
                                }

                                long processingTime = System.currentTimeMillis() - startTime;
                                log.info("Response generated for session {} (turn {}, threat: {}, {}ms)",
                                        sessionId, turnCount, threatCategory, processingTime);

                                ChatResponse externalResponse = ChatResponse.external(aiResponse);
                                return ApiResponse.ok(externalResponse, "Message processed successfully");
                            });
                })
                .exceptionally(e -> {
                    log.error("Error processing chat for session {}: {}", sessionId, e.getMessage(), e);
                    // Unwrap CompletionException
                    Throwable cause = e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;

                    if (cause instanceof IllegalStateException) {
                        return ApiResponse.<ChatResponse>serviceUnavailable("AI service configuration error")
                                .toResponseEntity();
                    }
                    return ApiResponse.<ChatResponse>internalError("CHAT_ERROR", "Failed to process message")
                            .toResponseEntity();
                });
    }

    /**
     * Generate response asynchronously using configured LLM provider
     */
    private java.util.concurrent.CompletableFuture<String> generateResponseAsync(String userMessage,
            List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {

        if (huggingFaceService != null) {
            return huggingFaceService.generateResponseAsync(userMessage, conversationHistory, scamType, threatLevel);
        } else if (ollamaService != null) {
            return ollamaService.generateResponseAsync(userMessage, conversationHistory, scamType, threatLevel);
        } else {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "No LLM service configured. Please set llm.provider to 'ollama' or 'huggingface'"));
        }
    }

    /**
     * Health check endpoint
     * GET /api/health
     * 
     * @return 200 OK with health status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> healthData = Map.of(
                "status", "healthy",
                "service", "varutri-honeypot",
                "llmProvider", llmProvider,
                "huggingFaceAvailable", huggingFaceService != null,
                "ollamaAvailable", ollamaService != null);

        return ApiResponse.ok(healthData, "Service is healthy");
    }

    /**
     * Comprehensive threat assessment endpoint
     * POST /api/assess
     * 
     * @return 200 OK with threat assessment
     * @return 400 Bad Request on validation errors
     */
    @PostMapping("/assess")
    public ResponseEntity<ApiResponse<ThreatAssessmentResponse>> assessThreat(
            @Valid @RequestBody ChatRequest request) {
        try {
            String sessionId = request.getSessionId();
            String userMessage = request.getMessage().getText();

            // Sanitize input
            userMessage = inputSanitizer.sanitizeMessageText(userMessage);

            log.info("Threat assessment requested for session: {}", sessionId);

            // Perform comprehensive ensemble threat analysis
            EnsembleThreatScorer.ThreatAssessment assessment = ensembleThreatScorer.assessThreat(userMessage,
                    request.getConversationHistory());

            // Convert to API response
            ThreatAssessmentResponse response = ThreatAssessmentResponse.fromAssessment(assessment);

            log.info("Assessment complete: {} ({}% confidence)",
                    response.getThreatLevel(),
                    response.getConfidencePercent());

            return ApiResponse.ok(response, "Threat assessment completed");

        } catch (Exception e) {
            log.error("Error in threat assessment: {}", e.getMessage(), e);
            return ApiResponse.<ThreatAssessmentResponse>internalError("ASSESSMENT_ERROR",
                    "Failed to complete threat assessment").toResponseEntity();
        }
    }

    /**
     * Manually trigger callback for a session
     * POST /api/callback/{sessionId}
     * 
     * @return 200 OK on success
     * @return 404 Not Found if session doesn't exist
     */
    @PostMapping("/callback/{sessionId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> triggerCallback(@PathVariable String sessionId) {
        log.info("Manual callback triggered for session: {}", sessionId);

        EvidenceCollector.EvidencePackage evidence = evidenceCollector.getEvidence(sessionId);
        if (evidence == null) {
            throw new ResourceNotFoundException("Session", sessionId);
        }

        sendFinalCallback(sessionId);

        Map<String, String> result = Map.of(
                "sessionId", sessionId,
                "status", "callback_sent");

        return ApiResponse.ok(result, "Callback sent successfully");
    }

    /**
     * Get evidence for a specific session
     * GET /api/evidence/{sessionId}
     * 
     * @return 200 OK with evidence
     * @return 404 Not Found if no evidence exists
     */
    @GetMapping("/evidence/{sessionId}")
    public ResponseEntity<ApiResponse<EvidenceCollector.EvidencePackage>> getEvidence(
            @PathVariable String sessionId) {
        log.info("Evidence requested for session: {}", sessionId);

        EvidenceCollector.EvidencePackage evidence = evidenceCollector.getEvidence(sessionId);

        if (evidence == null) {
            throw new ResourceNotFoundException("Evidence", sessionId);
        }

        return ApiResponse.ok(evidence, "Evidence retrieved successfully");
    }

    /**
     * Get all high-threat evidence packages
     * GET /api/evidence/high-threat
     * 
     * @return 200 OK with list of high-threat evidence
     */
    @GetMapping("/evidence/high-threat")
    public ResponseEntity<ApiResponse<List<EvidenceCollector.EvidencePackage>>> getHighThreatEvidence() {
        log.info("High-threat evidence requested");
        List<EvidenceCollector.EvidencePackage> evidence = evidenceCollector.getHighThreatEvidence();

        return ApiResponse.ok(evidence,
                String.format("Retrieved %d high-threat evidence package(s)", evidence.size()));
    }

    /**
     * Get all evidence packages
     * GET /api/evidence
     * 
     * @return 200 OK with list of all evidence
     */
    @GetMapping("/evidence")
    public ResponseEntity<ApiResponse<List<EvidenceCollector.EvidencePackage>>> getAllEvidence() {
        log.info("All evidence requested");
        List<EvidenceCollector.EvidencePackage> evidence = evidenceCollector.getAllEvidence();

        return ApiResponse.ok(evidence,
                String.format("Retrieved %d evidence package(s)", evidence.size()));
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Merge conversation histories
     */
    private List<ChatRequest.ConversationMessage> mergeConversationHistory(
            String sessionId,
            List<ChatRequest.ConversationMessage> requestHistory) {
        if (requestHistory != null && !requestHistory.isEmpty()) {
            return requestHistory;
        }
        return sessionStore.getConversationHistory(sessionId);
    }

    /**
     * Generate response using configured LLM provider
     */
    private String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        if (huggingFaceService != null) {
            return huggingFaceService.generateResponse(userMessage, conversationHistory, scamType, threatLevel);
        } else if (ollamaService != null) {
            return ollamaService.generateResponse(userMessage, conversationHistory, scamType, threatLevel);
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
            EvidenceCollector.EvidencePackage evidence = evidenceCollector.getEvidence(sessionId);

            if (evidence != null) {
                String agentNotes = String.format("Scam type: %s, Threat level: %.2f, Engagement successful",
                        evidence.getScamType(), evidence.getThreatLevel());

                callbackService.sendFinalReport(
                        sessionId,
                        evidence.getExtractedInfo(),
                        evidence.getConversation().size(),
                        agentNotes);

                log.info("Final callback sent to GUVI for session {}", sessionId);
            } else {
                log.warn("No evidence found for session {}, skipping callback", sessionId);
            }

            sessionStore.clearSession(sessionId);

        } catch (Exception e) {
            log.error("Error sending final callback for session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}
