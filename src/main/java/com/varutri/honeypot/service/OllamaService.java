package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.OllamaRequest;
import com.varutri.honeypot.dto.OllamaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Service for communicating with Ollama LLM
 * 
 * Now integrated with PromptHardeningService and ContextWindowManager
 * for consistent response quality with HuggingFaceService
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaService {

    private final WebClient webClient;
    private final String model;
    private final PersonaService personaService;

    @Autowired
    @Lazy
    private PromptHardeningService promptHardeningService;

    @Autowired
    @Lazy
    private ContextWindowManager contextWindowManager;

    @Autowired
    @Lazy
    private ResponseValidationService responseValidationService;

    @Value("${validation.max-retries:2}")
    private int maxRetries;

    public OllamaService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String model,
            @Value("${ollama.timeout:30}") int timeout,
            PersonaService personaService) {
        this.model = model;
        this.personaService = personaService;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl != null ? baseUrl : "http://localhost:11434")
                .build();

        log.info("Ollama service initialized with model: {} at {}", model, baseUrl);
        log.info("Using persona: {}", personaService.getPersonaSummary());
    }

    /**
     * Generate response using Ollama
     * Includes Phase 3 validation with retry logic
     */
    public String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {

        int attempts = 0;
        String lastResponse = null;
        ResponseValidationService.ValidationResult lastValidation = null;

        while (attempts <= maxRetries) {
            try {
                String prompt;
                if (attempts == 0) {
                    prompt = buildPrompt(userMessage, conversationHistory, scamType, threatLevel);
                } else {
                    // Add retry guidance
                    prompt = buildRetryPrompt(userMessage, conversationHistory, scamType, threatLevel, lastValidation);
                    log.info("Ollama retry attempt {} with validation guidance", attempts);
                }

                OllamaRequest request = new OllamaRequest();
                request.setModel(model);
                request.setPrompt(prompt);
                request.setStream(false);
                request.setOptions(new OllamaRequest.OllamaOptions());

                log.debug("Sending request to Ollama (attempt {})", attempts + 1);

                Mono<OllamaResponse> responseMono = webClient.post()
                        .uri("/api/generate")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(OllamaResponse.class)
                        .timeout(Duration.ofSeconds(30));

                OllamaResponse response = responseMono.block();

                if (response != null && response.getResponse() != null) {
                    lastResponse = response.getResponse().trim();
                    log.info("Received response from Ollama (length: {}, attempt: {})",
                            lastResponse.length(), attempts + 1);

                    // === PHASE 3: RESPONSE VALIDATION ===
                    if (responseValidationService != null) {
                        lastValidation = responseValidationService.validateResponse(
                                lastResponse, userMessage, threatLevel);

                        if (lastValidation.isPassed()) {
                            log.info("Ollama response validation PASSED: {}", lastValidation.getSummary());
                            return lastResponse;
                        } else {
                            log.warn("Ollama response validation FAILED (attempt {}): {}",
                                    attempts + 1, lastValidation.getSummary());

                            // Try to sanitize
                            if (lastValidation.canBeSanitized()) {
                                String sanitized = responseValidationService.sanitizeResponse(
                                        lastResponse, lastValidation);
                                ResponseValidationService.ValidationResult sanitizedValidation = responseValidationService
                                        .validateResponse(sanitized, userMessage, threatLevel);
                                if (sanitizedValidation.isPassed()) {
                                    log.info("Sanitized Ollama response passed validation");
                                    return sanitized;
                                }
                            }

                            if (lastValidation.needsRegeneration() && attempts < maxRetries) {
                                attempts++;
                                continue;
                            }
                        }
                    } else {
                        return lastResponse;
                    }
                } else {
                    log.error("Received null or empty response from Ollama");
                    lastResponse = null;
                }

                attempts++;

            } catch (Exception e) {
                log.error("Error calling Ollama API (attempt {}): {}", attempts + 1, e.getMessage());
                attempts++;
            }
        }

        // === PHASE 4: FALLBACK RESPONSE ===
        log.warn("All Ollama attempts failed, using fallback response");
        return getFallbackResponse(threatLevel);
    }

    /**
     * Generate response asynchronously
     * Wraps the synchronous retry logic in a CompletableFuture
     */
    public java.util.concurrent.CompletableFuture<String> generateResponseAsync(String userMessage,
            List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        return java.util.concurrent.CompletableFuture
                .supplyAsync(() -> generateResponse(userMessage, conversationHistory, scamType, threatLevel));
    }

    /**
     * Build retry prompt with guidance from previous validation failure
     */
    private String buildRetryPrompt(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel, ResponseValidationService.ValidationResult previousValidation) {

        String basePrompt = buildPrompt(userMessage, conversationHistory, scamType, threatLevel);

        if (previousValidation != null && responseValidationService != null) {
            return basePrompt + "\n\n" + responseValidationService.getSuggestedFixes(previousValidation);
        }

        return basePrompt;
    }

    /**
     * Get fallback response when all attempts fail
     */
    private String getFallbackResponse(double threatLevel) {
        String[] fallbacks;

        if (threatLevel >= 0.6) {
            fallbacks = new String[] {
                    "Beta, my phone is giving problem. Can you send again?",
                    "Sorry beta, I didn't understand. Explain again?",
                    "Wait wait. Let me ask my son first.",
                    "One minute, I need to check something.",
                    "Hmm, my eyes are not good today. What?"
            };
        } else {
            fallbacks = new String[] {
                    "Sorry, I didn't understand. Say again?",
                    "Acha? Tell me more.",
                    "Hmm, I see. What should I do?",
                    "Ok, but I am little confused...",
                    "Really? Tell me more."
            };
        }

        int index = (int) (Math.random() * fallbacks.length);
        return fallbacks[index];
    }

    /**
     * Build prompt with persona and conversation context
     * Uses PromptHardeningService and ContextWindowManager when available
     */
    private String buildPrompt(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        StringBuilder prompt = new StringBuilder();

        // === PHASE 1: PROMPT HARDENING ===
        String systemPrompt;

        if (promptHardeningService != null) {
            // Check for prompt injection attempts
            PromptHardeningService.PromptInjectionAnalysis injectionCheck = promptHardeningService
                    .analyzeForInjection(userMessage);

            if (injectionCheck.isDetected()) {
                log.warn("Prompt injection attempt detected in Ollama request! Risk: {}",
                        String.format("%.0f%%", injectionCheck.getRiskScore() * 100));
                threatLevel = Math.max(threatLevel, 0.7);
            }

            // Determine threat category
            String threatCategory = getThreatCategory(threatLevel);
            int triggeredLayers = estimateTriggeredLayers(threatLevel);

            // Build hardened prompt
            PromptHardeningService.HardenedPrompt hardenedPrompt = promptHardeningService.buildSystemPrompt(scamType,
                    threatLevel,
                    threatCategory, triggeredLayers);

            systemPrompt = hardenedPrompt.getSystemPrompt();
            log.debug("Using hardened prompt for Ollama: {} tokens", hardenedPrompt.getEstimatedTokens());
        } else {
            // Fallback to legacy prompt
            systemPrompt = personaService.getCurrentPersonaPrompt();

            if (threatLevel >= 0.6) {
                systemPrompt += "\n\nSYSTEM ALERT: This user is a suspected scammer (Type: " + scamType + "). " +
                        "Be extra cautious. Stall them. Do NOT give money yet.";
            }
        }

        prompt.append(systemPrompt);
        prompt.append("\n\n");

        // === PHASE 1.5: CONTEXT WINDOW MANAGEMENT ===
        List<ChatRequest.ConversationMessage> managedHistory = conversationHistory;

        if (contextWindowManager != null && conversationHistory != null && !conversationHistory.isEmpty()) {
            ContextWindowManager.ManagedContext managedContext = contextWindowManager.buildContext(
                    systemPrompt,
                    conversationHistory,
                    null,
                    userMessage);

            // Add intelligence summary if present
            if (managedContext.getIntelligenceSummary() != null &&
                    !managedContext.getIntelligenceSummary().isEmpty()) {
                prompt.append(managedContext.getIntelligenceSummary()).append("\n\n");
            }

            // Add conversation summary if present
            if (managedContext.getConversationSummary() != null &&
                    !managedContext.getConversationSummary().isEmpty()) {
                prompt.append(managedContext.getConversationSummary()).append("\n\n");
            }

            managedHistory = managedContext.getRecentMessages();

            if (managedContext.isWasTruncated()) {
                log.info("Ollama context truncated: {} turns kept, {} summarized",
                        managedContext.getTurnsPreserved(),
                        managedContext.getTurnsSummarized());
            }
        }

        // Conversation history
        if (managedHistory != null && !managedHistory.isEmpty()) {
            prompt.append("Previous conversation:\n");
            for (ChatRequest.ConversationMessage msg : managedHistory) {
                String role = "user".equals(msg.getSender()) || "scammer".equals(msg.getSender())
                        ? "Them"
                        : "You";
                prompt.append(role).append(": ").append(msg.getText()).append("\n");
            }
            prompt.append("\n");
        }

        // Current message
        prompt.append("Them: ").append(userMessage).append("\n");
        prompt.append("You: ");

        return prompt.toString();
    }

    private String getThreatCategory(double threatLevel) {
        if (threatLevel >= 0.8)
            return "CRITICAL";
        if (threatLevel >= 0.6)
            return "HIGH";
        if (threatLevel >= 0.4)
            return "MEDIUM";
        if (threatLevel >= 0.2)
            return "LOW";
        return "SAFE";
    }

    private int estimateTriggeredLayers(double threatLevel) {
        if (threatLevel >= 0.8)
            return 5;
        if (threatLevel >= 0.6)
            return 4;
        if (threatLevel >= 0.4)
            return 3;
        if (threatLevel >= 0.2)
            return 2;
        if (threatLevel > 0)
            return 1;
        return 0;
    }
}
