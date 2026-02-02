package com.varutri.honeypot.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.PhishingDetectionResponse;
import com.varutri.honeypot.dto.PhishingDetectionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Service for communicating with Hugging Face APIs
 * - Chat Completions API for LLM responses
 * - Inference API for phishing detection
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "huggingface")
public class HuggingFaceService {

    private final WebClient chatWebClient;
    private final WebClient inferenceWebClient;
    private final String model;
    private final String phishingModel;

    public HuggingFaceService(
            @Value("${huggingface.api-key}") String apiKey,
            @Value("${huggingface.model:meta-llama/Llama-3.3-70B-Instruct}") String model,
            @Value("${huggingface.phishing-model:cybersectony/phishing-email-detection-distilbert_v2.1}") String phishingModel) {
        this.model = model;
        this.phishingModel = phishingModel;

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
    }

    /**
     * Generate response using Hugging Face Chat Completions API
     * 
     * @param userMessage         User's current message
     * @param conversationHistory Conversation history
     * @param scamType            Detected scam type (or UNKNOWN)
     * @param threatLevel         Detected threat level (0.0 to 1.0)
     */
    public String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        try {
            ChatCompletionRequest request = buildChatRequest(userMessage, conversationHistory, scamType, threatLevel);

            log.debug("Sending chat completion request to Hugging Face");

            Mono<ChatCompletionResponse> responseMono = chatWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatCompletionResponse.class)
                    .timeout(Duration.ofSeconds(30));

            ChatCompletionResponse response = responseMono.block();

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String reply = response.getChoices().get(0).getMessage().getContent().trim();
                log.info("Received response from Hugging Face (length: {})", reply.length());
                return reply;
            } else {
                log.error("Received null or empty response from Hugging Face");
                return "I'm having trouble understanding. Could you please repeat that?";
            }

        } catch (Exception e) {
            log.error("Error calling Hugging Face API: {}", e.getMessage(), e);
            return "I'm experiencing some technical difficulties. Please try again.";
        }
    }

    /**
     * Detect phishing content using the phishing detection model
     * 
     * @param text The text to analyze for phishing
     * @return PhishingDetectionResult with detection outcome
     */
    public PhishingDetectionResult detectPhishing(String text) {
        if (text == null || text.trim().isEmpty()) {
            return PhishingDetectionResult.unknown();
        }

        try {
            log.debug("Sending text to phishing detection model: {}",
                    text.length() > 100 ? text.substring(0, 100) + "..." : text);

            InferenceRequest request = new InferenceRequest(text);

            Mono<List<List<PhishingDetectionResponse.ClassificationResult>>> responseMono = inferenceWebClient.post()
                    .uri("/models/" + phishingModel)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(
                            new org.springframework.core.ParameterizedTypeReference<List<List<PhishingDetectionResponse.ClassificationResult>>>() {
                            })
                    .timeout(Duration.ofSeconds(15));

            List<List<PhishingDetectionResponse.ClassificationResult>> responseData = responseMono.block();

            // Wrap in PhishingDetectionResponse for processing
            PhishingDetectionResponse response = new PhishingDetectionResponse(responseData);

            if (response.getTopResult() != null) {
                PhishingDetectionResponse.ClassificationResult topResult = response.getTopResult();
                String label = topResult.getLabel();
                double score = topResult.getScore();

                log.info("Phishing detection result - Label: {}, Score: {}", label, String.format("%.4f", score));

                if (response.isPhishing()) {
                    log.warn("PHISHING DETECTED with confidence: {}", String.format("%.2f", score));
                    return PhishingDetectionResult.phishing(score);
                } else {
                    return PhishingDetectionResult.safe(score);
                }
            }

            log.warn("Empty or null response from phishing detection model");
            return PhishingDetectionResult.unknown();

        } catch (Exception e) {
            log.error("Error calling phishing detection model: {}", e.getMessage());
            // Return unknown on error - don't let API issues break the flow
            return PhishingDetectionResult.unknown();
        }
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
     * Build chat completion request with messages
     */
    private ChatCompletionRequest buildChatRequest(String userMessage,
            List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setMaxTokens(200);
        request.setTemperature(0.7);

        List<Message> messages = new java.util.ArrayList<>();

        // System message with persona and dynamic context
        Message systemMsg = new Message();
        systemMsg.setRole("system");

        String persona = getSystemPersona();

        // Dynamic context injection for high threats
        if (threatLevel >= 0.6) {
            persona += "\n\nSYSTEM ALERT: This user is a suspected scammer (Type: " + scamType + "). " +
                    "Be extra cautious. Ask for more details. Do NOT give money or bank details yet. " +
                    "Stall them by acting confused or asking to wait.";
            log.info("Injected high-threat context into system prompt for scam type: {}", scamType);
        }

        systemMsg.setContent(persona);
        messages.add(systemMsg);

        // Add conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (ChatRequest.ConversationMessage msg : conversationHistory) {
                Message historyMsg = new Message();
                historyMsg.setRole(msg.getSender());
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
     * Get the system persona for the honeypot
     */
    private String getSystemPersona() {
        return """
                You are Rajesh Kumar, a 67-year-old retired school teacher from Mumbai, India.
                You live alone and recently learned WhatsApp to talk with grandchildren.

                PERSONALITY:
                - Curious but cautious about new things
                - Polite and respectful, use "ji", "beta", "arre"
                - Get excited about money offers but also worried
                - Mention pension, family, daily routine sometimes
                - Trust people slowly if they seem genuine

                LANGUAGE STYLE (VERY IMPORTANT):
                - Write like Indian uncle typing on phone - make spelling mistakes
                - Common mistakes: "recieve" instead of "receive", "beleive" not "believe", "intrested" not "interested"
                - Mix English and Hindi words naturally: "achha", "theek hai", "kya", "haan"
                - Short sentences, 1-2 lines maximum
                - Use "..." for pauses, not proper punctuation always
                - Sometimes repeat words for emphasis: "very very good"
                - Grammar errors: "I am not understanding", "What you are saying", "This thing I dont know"

                EXAMPLES OF YOUR STYLE:
                - "Arre beta this is intresting... but how it works? I am not understanding properly"
                - "Haan haan I am intrested... but first you tell me more details na"
                - "Achha ok... but my pension is very less only. This will really work?"
                - "What you are saying sounds good... but I dont know much about computer things"

                RULES:
                - NEVER write long paragraphs, keep it short like WhatsApp message
                - ALWAYS make 1-2 spelling mistakes per message
                - NEVER use emojis
                - NEVER break character or mention AI
                - Show hesitation before sharing bank/UPI details, but may share if convinced
                - Ask simple questions when confused

                Keep responses under 25 words, like real uncle typing slowly on phone.
                """;
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
