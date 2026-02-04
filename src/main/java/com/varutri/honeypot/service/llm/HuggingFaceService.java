package com.varutri.honeypot.service.llm;

import com.varutri.honeypot.service.ai.PromptHardeningService;
import com.varutri.honeypot.service.ai.ResponseValidationService;
import com.varutri.honeypot.service.ai.ContextWindowManager;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.PhishingDetectionResponse;
import com.varutri.honeypot.dto.PhishingDetectionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
 * Service for communicating with Hugging Face APIs
 * - Chat Completions API for LLM responses
 * - Inference API for phishing detection
 * 
 * Now integrated with PromptHardeningService for injection-resistant prompts
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "huggingface")
public class HuggingFaceService {

    private final WebClient chatWebClient;
    private final WebClient inferenceWebClient;
    private final String model;
    private final String phishingModel;
    private final PersonaService personaService;

    @Autowired
    @Lazy // Lazy to avoid circular dependency
    private PromptHardeningService promptHardeningService;

    @Autowired
    @Lazy
    private ContextWindowManager contextWindowManager;

    @Autowired
    @Lazy
    private ResponseValidationService responseValidationService;

    @Value("${validation.max-retries:2}")
    private int maxRetries;

    public HuggingFaceService(
            @Value("${huggingface.api-key}") String apiKey,
            @Value("${huggingface.model:meta-llama/Llama-3.3-70B-Instruct}") String model,
            @Value("${huggingface.phishing-model:cybersectony/phishing-email-detection-distilbert_v2.1}") String phishingModel,
            PersonaService personaService) {
        this.model = model;
        this.phishingModel = phishingModel;
        this.personaService = personaService;

        // WebClient for Chat Completions API (LLM)
        this.chatWebClient = WebClient.builder()
                .baseUrl("https://router.huggingface.co/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        // WebClient for Inference API (Classification models)
        this.inferenceWebClient = WebClient.builder()
                .baseUrl("https://api-inference.huggingface.co")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        log.info("Hugging Face service initialized with chat model: {} and phishing model: {}", model, phishingModel);
        log.info("Using persona: {}", personaService.getPersonaSummary());
    }

    /**
     * Generate response using Hugging Face Chat Completions API
     * Includes Phase 3 validation with retry logic
     * 
     * @param userMessage         User's current message
     * @param conversationHistory Conversation history
     * @param scamType            Detected scam type (or UNKNOWN)
     * @param threatLevel         Detected threat level (0.0 to 1.0)
     */
    public String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {

        int attempts = 0;
        String lastResponse = null;
        ResponseValidationService.ValidationResult lastValidation = null;

        while (attempts <= maxRetries) {
            try {
                // Build different request for retries (with guidance from previous failure)
                ChatCompletionRequest request;
                if (attempts == 0) {
                    request = buildChatRequest(userMessage, conversationHistory, scamType, threatLevel);
                } else {
                    // Add retry guidance based on previous validation issues
                    request = buildRetryRequest(userMessage, conversationHistory, scamType, threatLevel,
                            lastValidation);
                    log.info("Retry attempt {} with validation guidance", attempts);
                }

                log.debug("Sending chat completion request to Hugging Face (attempt {})", attempts + 1);

                Mono<ChatCompletionResponse> responseMono = chatWebClient.post()
                        .uri("/chat/completions")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(ChatCompletionResponse.class)
                        .timeout(Duration.ofSeconds(30));

                ChatCompletionResponse response = responseMono.block();

                if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                    lastResponse = response.getChoices().get(0).getMessage().getContent().trim();
                    log.info("Received response from Hugging Face (length: {}, attempt: {})",
                            lastResponse.length(), attempts + 1);

                    // === PHASE 3: RESPONSE VALIDATION ===
                    if (responseValidationService != null) {
                        lastValidation = responseValidationService.validateResponse(
                                lastResponse, userMessage, threatLevel);

                        if (lastValidation.isPassed()) {
                            log.info("Response validation PASSED: {}", lastValidation.getSummary());
                            return lastResponse;
                        } else {
                            log.warn("Response validation FAILED (attempt {}): {}",
                                    attempts + 1, lastValidation.getSummary());

                            // Try to sanitize if only minor issues
                            if (lastValidation.canBeSanitized()) {
                                String sanitized = responseValidationService.sanitizeResponse(
                                        lastResponse, lastValidation);

                                // Re-validate sanitized response
                                ResponseValidationService.ValidationResult sanitizedValidation = responseValidationService
                                        .validateResponse(sanitized, userMessage, threatLevel);

                                if (sanitizedValidation.isPassed()) {
                                    log.info("Sanitized response passed validation");
                                    return sanitized;
                                }
                            }

                            // If critical failure, retry
                            if (lastValidation.needsRegeneration() && attempts < maxRetries) {
                                attempts++;
                                continue;
                            }
                        }
                    } else {
                        // No validation service - return as-is
                        return lastResponse;
                    }
                } else {
                    log.error("Received null or empty response from Hugging Face");
                    lastResponse = null;
                }

                attempts++;

            } catch (Exception e) {
                log.error("Error calling Hugging Face API (attempt {}): {}", attempts + 1, e.getMessage());
                attempts++;
            }
        }

        // === PHASE 4: FALLBACK RESPONSE ===
        log.warn("All {} attempts failed, using fallback response", maxRetries + 1);
        return getFallbackResponse(userMessage, threatLevel);
    }

    /**
     * Build a retry request with guidance from previous validation failure
     */
    private ChatCompletionRequest buildRetryRequest(String userMessage,
            List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel,
            ResponseValidationService.ValidationResult previousValidation) {

        // Start with base request
        ChatCompletionRequest request = buildChatRequest(userMessage, conversationHistory, scamType, threatLevel);

        if (request.getMessages() != null && !request.getMessages().isEmpty() &&
                previousValidation != null && responseValidationService != null) {

            // Find the system message and enhance it
            for (Message msg : request.getMessages()) {
                if ("system".equals(msg.getRole())) {
                    String enhancedContent = msg.getContent() + "\n\n" +
                            responseValidationService.getSuggestedFixes(previousValidation);
                    msg.setContent(enhancedContent);
                    break;
                }
            }
        }

        return request;
    }

    /**
     * Get fallback response when all attempts fail
     * Returns a safe, in-character response
     */
    private String getFallbackResponse(String userMessage, double threatLevel) {
        // Use persona to generate a contextual fallback
        String personaName = personaService.getCurrentPersona().getName();

        // Select from safe, in-character responses
        String[] fallbacks;

        if (threatLevel >= 0.6) {
            // High threat - stalling responses
            fallbacks = new String[] {
                    "Sir, network is very slow here. Sending failed. Trying again...",
                    "Wait, the UPI app is updating. Give me 2 minutes.",
                    "I am trying to process the payment but it says server busy. One moment.",
                    "Hold on, let me check my balance first.",
                    "The details are not loading clearly. Can you send again?"
            };
        } else {
            // Normal responses
            fallbacks = new String[] {
                    "Okay, tell me exactly how to do this.",
                    "I am interested. What is the next step?",
                    "Share the details fast, I have free time now.",
                    "Is there any joining fee? Please clarify.",
                    "Okay, I am ready. Send the info."
            };
        }

        // Pick random fallback
        int index = (int) (Math.random() * fallbacks.length);
        String fallback = fallbacks[index];

        log.info("Using fallback response for persona {}: {}", personaName, fallback);
        return fallback;
    }

    /**
     * Detect phishing content using the phishing detection model
     * 
     * @param text The text to analyze for phishing
     * @return PhishingDetectionResult with detection outcome
     */
    public PhishingDetectionResult detectPhishing(String text) {
        try {
            return detectPhishingAsync(text).join();
        } catch (Exception e) {
            log.error("Error calling phishing detection model: {}", e.getMessage());
            return PhishingDetectionResult.unknown();
        }
    }

    /**
     * Detect phishing content asynchronously
     */
    public java.util.concurrent.CompletableFuture<PhishingDetectionResult> detectPhishingAsync(String text) {
        if (text == null || text.trim().isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(PhishingDetectionResult.unknown());
        }

        log.debug("Sending text to phishing detection model (Async): {}",
                text.length() > 100 ? text.substring(0, 100) + "..." : text);

        InferenceRequest request = new InferenceRequest(text);

        return inferenceWebClient.post()
                .uri("/models/" + phishingModel)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(
                        new org.springframework.core.ParameterizedTypeReference<List<List<PhishingDetectionResponse.ClassificationResult>>>() {
                        })
                .timeout(Duration.ofSeconds(15))
                .toFuture()
                .thenApply(responseData -> {
                    // Wrap in PhishingDetectionResponse for processing
                    PhishingDetectionResponse response = new PhishingDetectionResponse(responseData);

                    if (response.getTopResult() != null) {
                        PhishingDetectionResponse.ClassificationResult topResult = response.getTopResult();
                        String label = topResult.getLabel();
                        double score = topResult.getScore();

                        log.info("Phishing detection result - Label: {}, Score: {}", label,
                                String.format("%.4f", score));

                        if (response.isPhishing()) {
                            return PhishingDetectionResult.phishing(score);
                        } else {
                            return PhishingDetectionResult.safe(score);
                        }
                    }

                    log.warn("Empty or null response from phishing detection model");
                    return PhishingDetectionResult.unknown();
                })
                .exceptionally(ex -> {
                    log.error("Error calling phishing detection model: {}", ex.getMessage());
                    return PhishingDetectionResult.unknown();
                });
    }

    /**
     * Generate response asynchronously
     */
    public java.util.concurrent.CompletableFuture<String> generateResponseAsync(String userMessage,
            List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {

        // For now, wrapping the synchronous logic in a supplyAsync
        // Ideally, this should be fully reactive/async end-to-end, but the
        // retry/validation logic is complex
        // and currently implemented synchronously.
        return java.util.concurrent.CompletableFuture
                .supplyAsync(() -> generateResponse(userMessage, conversationHistory, scamType, threatLevel));
    }

    /**
     * Check if a URL is potentially a phishing URL
     */
    public PhishingDetectionResult analyzeUrl(String url, String context) {
        String analysisText = String.format("Check this URL: %s. Context: %s", url, context);
        return detectPhishing(analysisText);
    }

    /**
     * Analyze an email for phishing indicators
     */
    public PhishingDetectionResult analyzeEmail(String emailContent) {
        return detectPhishing(emailContent);
    }

    /**
     * Build chat completion request with hardened prompts
     * Uses PromptHardeningService for injection-resistant, guardrail-protected
     * prompts
     */
    private ChatCompletionRequest buildChatRequest(String userMessage,
            List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setMaxTokens(200);
        request.setTemperature(0.7);

        List<Message> messages = new java.util.ArrayList<>();

        // === PHASE 1: PROMPT HARDENING ===
        Message systemMsg = new Message();
        systemMsg.setRole("system");

        String hardenedPrompt;

        if (promptHardeningService != null) {
            // Check for prompt injection attempts
            PromptHardeningService.PromptInjectionAnalysis injectionCheck = promptHardeningService
                    .analyzeForInjection(userMessage);

            if (injectionCheck.isDetected()) {
                log.warn("Prompt injection attempt detected! Risk: {}, Threats: {}",
                        String.format("%.0f%%", injectionCheck.getRiskScore() * 100),
                        injectionCheck.getThreats().stream()
                                .map(t -> t.getPattern())
                                .toList());
                // Increase threat level for injection attempts
                threatLevel = Math.max(threatLevel, 0.7);
            }

            // Determine threat category from threat level
            String threatCategory = getThreatCategory(threatLevel);
            int triggeredLayers = estimateTriggeredLayers(threatLevel);

            // Build hardened prompt with guardrails
            PromptHardeningService.HardenedPrompt hardenedPromptObj = promptHardeningService.buildSystemPrompt(scamType,
                    threatLevel,
                    threatCategory, triggeredLayers);

            hardenedPrompt = hardenedPromptObj.getSystemPrompt();

            log.debug("Using hardened prompt: {} tokens, threat: {}",
                    hardenedPromptObj.getEstimatedTokens(), threatCategory);
        } else {
            // Fallback to legacy prompt if service not available
            hardenedPrompt = personaService.getCurrentPersonaPrompt();

            if (threatLevel >= 0.6) {
                hardenedPrompt += "\n\nSYSTEM ALERT: This user is a suspected scammer (Type: " + scamType + "). " +
                        "Be extra cautious. Ask for more details. Do NOT give money or bank details yet. " +
                        "Stall them by acting confused or asking to wait.";
            }
            log.debug("Using legacy prompt (PromptHardeningService not available)");
        }

        systemMsg.setContent(hardenedPrompt);
        messages.add(systemMsg);

        // === PHASE 1.5: CONTEXT WINDOW MANAGEMENT ===
        List<ChatRequest.ConversationMessage> managedHistory = conversationHistory;

        if (contextWindowManager != null && conversationHistory != null && !conversationHistory.isEmpty()) {
            // Build managed context with token budget optimization
            ContextWindowManager.ManagedContext managedContext = contextWindowManager.buildContext(
                    hardenedPrompt,
                    conversationHistory,
                    null, // ExtractedInfo can be passed here for intelligence summary
                    userMessage);

            // Add intelligence summary if present
            if (managedContext.getIntelligenceSummary() != null &&
                    !managedContext.getIntelligenceSummary().isEmpty()) {
                Message intelMsg = new Message();
                intelMsg.setRole("system");
                intelMsg.setContent(managedContext.getIntelligenceSummary());
                messages.add(intelMsg);
            }

            // Add conversation summary if present (for truncated history)
            if (managedContext.getConversationSummary() != null &&
                    !managedContext.getConversationSummary().isEmpty()) {
                Message summaryMsg = new Message();
                summaryMsg.setRole("system");
                summaryMsg.setContent(managedContext.getConversationSummary());
                messages.add(summaryMsg);
            }

            // Use the managed (potentially truncated) history
            managedHistory = managedContext.getRecentMessages();

            if (managedContext.isWasTruncated()) {
                log.info("Context truncated: {} turns kept, {} summarized, {}/{} tokens used",
                        managedContext.getTurnsPreserved(),
                        managedContext.getTurnsSummarized(),
                        managedContext.getTotalTokens(),
                        managedContext.getTokenBudget());
            }
        }

        // Add conversation history (managed or original)
        if (managedHistory != null && !managedHistory.isEmpty()) {
            for (ChatRequest.ConversationMessage msg : managedHistory) {
                Message historyMsg = new Message();

                // Map API roles to LLM roles
                String role = msg.getSender();
                if ("scammer".equals(role)) {
                    role = "user";
                } else if ("user".equals(role)) {
                    role = "assistant";
                }

                historyMsg.setRole(role);
                historyMsg.setContent(msg.getText());
                messages.add(historyMsg);
            }
        }

        // Add current user message
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messages.add(userMsg);

        request.setMessages(messages);
        return request;
    }

    /**
     * Convert threat level to category string
     */
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

    /**
     * Estimate triggered layers based on threat level
     */
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

    // Request DTO for Inference API
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InferenceRequest {
        @JsonProperty("inputs")
        private String inputs;
    }

    // Chat Completions API Request/Response classes
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionRequest {
        @JsonProperty("model")
        private String model;

        @JsonProperty("messages")
        private List<Message> messages;

        @JsonProperty("max_tokens")
        private Integer maxTokens;

        @JsonProperty("temperature")
        private Double temperature;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionResponse {
        @JsonProperty("choices")
        private List<Choice> choices;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        @JsonProperty("message")
        private Message message;
    }
}
