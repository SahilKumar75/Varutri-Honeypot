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
                .baseUrl("https://router.huggingface.co/models/" + model)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        log.info("Hugging Face service initialized with model: {}", model);
    }

    /**
     * Generate response using Hugging Face
     */
    public String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory) {
        try {
            String prompt = buildPrompt(userMessage, conversationHistory);

            HuggingFaceRequest request = new HuggingFaceRequest();
            request.setInputs(prompt);
            request.setParameters(new HuggingFaceRequest.Parameters(200, 0.7, 0.9, 50));

            log.debug("Sending request to Hugging Face with prompt length: {}", prompt.length());

            Mono<List<HuggingFaceResponse>> responseMono = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<HuggingFaceResponse>>() {
                    })
                    .timeout(Duration.ofSeconds(30));

            List<HuggingFaceResponse> responses = responseMono.block();

            if (responses != null && !responses.isEmpty() && responses.get(0).getGeneratedText() != null) {
                String reply = responses.get(0).getGeneratedText().trim();
                // Remove the prompt from the response (HF returns prompt + completion)
                if (reply.startsWith(prompt)) {
                    reply = reply.substring(prompt.length()).trim();
                }
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
     * Build prompt with persona and conversation context
     */
    private String buildPrompt(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory) {
        StringBuilder prompt = new StringBuilder();

        // System persona
        prompt.append(getSystemPersona()).append("\n\n");

        // Conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            prompt.append("Previous conversation:\n");
            for (ChatRequest.ConversationMessage msg : conversationHistory) {
                String role = "user".equals(msg.getRole()) ? "Them" : "You";
                prompt.append(role).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        // Current message
        prompt.append("Them: ").append(userMessage).append("\n");
        prompt.append("You: ");

        return prompt.toString();
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HuggingFaceRequest {
        @JsonProperty("inputs")
        private String inputs;

        @JsonProperty("parameters")
        private Parameters parameters;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Parameters {
            @JsonProperty("max_new_tokens")
            private int maxNewTokens;

            @JsonProperty("temperature")
            private double temperature;

            @JsonProperty("top_p")
            private double topP;

            @JsonProperty("top_k")
            private int topK;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HuggingFaceResponse {
        @JsonProperty("generated_text")
        private String generatedText;
    }
}
