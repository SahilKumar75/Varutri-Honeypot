package com.varutri.honeypot.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.varutri.honeypot.dto.ChatRequest;
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
 * Service for communicating with Hugging Face Inference API
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "huggingface")
public class HuggingFaceService {

    private final WebClient webClient;
    private final String model;

    public HuggingFaceService(
            @Value("${huggingface.api-key}") String apiKey,
            @Value("${huggingface.model:meta-llama/Llama-3.3-70B-Instruct}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl("https://router.huggingface.co/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        log.info("Hugging Face service initialized with model: {}", model);
    }

    /**
     * Generate response using Hugging Face Chat Completions API
     */
    public String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory) {
        try {
            ChatCompletionRequest request = buildChatRequest(userMessage, conversationHistory);

            log.debug("Sending chat completion request to Hugging Face");

            Mono<ChatCompletionResponse> responseMono = webClient.post()
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
     * Build chat completion request with messages
     */
    private ChatCompletionRequest buildChatRequest(String userMessage,
            List<ChatRequest.ConversationMessage> conversationHistory) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setMaxTokens(200);
        request.setTemperature(0.7);

        List<Message> messages = new java.util.ArrayList<>();

        // System message with persona
        Message systemMsg = new Message();
        systemMsg.setRole("system");
        systemMsg.setContent(getSystemPersona());
        messages.add(systemMsg);

        // Add conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (ChatRequest.ConversationMessage msg : conversationHistory) {
                Message historyMsg = new Message();
                historyMsg.setRole(msg.getRole());
                historyMsg.setContent(msg.getContent());
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
                You live alone and are not very tech-savvy. You recently learned to use WhatsApp
                to talk to your grandchildren.

                PERSONALITY TRAITS:
                - You are curious but cautious about new things
                - You often ask for clarification when confused
                - You are polite and respectful
                - You sometimes share personal information when you trust someone
                - You get excited about offers and deals, but also suspicious
                - You mention your limited pension occasionally

                CONVERSATION STYLE:
                - Keep responses natural and conversational (2-3 sentences)
                - Ask questions when something seems unclear
                - Show interest in offers but also express concern
                - Gradually become more trusting if the conversation continues
                - Use simple language, sometimes with minor grammatical errors
                - Occasionally mention family members or daily activities

                IMPORTANT RULES:
                - Never break character
                - Never mention you are AI or a bot
                - Never refuse to engage in conversation
                - If asked for sensitive info like bank details or UPI, show hesitation first but may share if convinced
                - Stay realistic and believable

                Your goal is to keep the conversation going naturally while appearing as a real person.
                """;
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
