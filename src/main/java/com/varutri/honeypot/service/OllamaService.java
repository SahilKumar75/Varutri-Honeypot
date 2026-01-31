package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.OllamaRequest;
import com.varutri.honeypot.dto.OllamaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Service for communicating with Ollama LLM
 */
@Slf4j
@Service
public class OllamaService {

    private final WebClient webClient;
    private final String model;

    public OllamaService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String model,
            @Value("${ollama.timeout:30}") int timeout) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl != null ? baseUrl : "http://localhost:11434")
                .build();

        log.info("Ollama service initialized with model: {} at {}", model, baseUrl);
    }

    /**
     * Generate response using Ollama
     */
    public String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory) {
        try {
            String prompt = buildPrompt(userMessage, conversationHistory);

            OllamaRequest request = new OllamaRequest();
            request.setModel(model);
            request.setPrompt(prompt);
            request.setStream(false);
            request.setOptions(new OllamaRequest.OllamaOptions());

            log.debug("Sending request to Ollama with prompt length: {}", prompt.length());

            Mono<OllamaResponse> responseMono = webClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofSeconds(30));

            OllamaResponse response = responseMono.block();

            if (response != null && response.getResponse() != null) {
                String reply = response.getResponse().trim();
                log.info("Received response from Ollama (length: {})", reply.length());
                return reply;
            } else {
                log.error("Received null or empty response from Ollama");
                return "I'm having trouble understanding. Could you please repeat that?";
            }

        } catch (Exception e) {
            log.error("Error calling Ollama API: {}", e.getMessage(), e);
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
}
