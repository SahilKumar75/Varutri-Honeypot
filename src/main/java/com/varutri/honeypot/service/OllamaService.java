package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.OllamaRequest;
import com.varutri.honeypot.dto.OllamaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaService {

    private final WebClient webClient;
    private final String model;
    private final PersonaService personaService;

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
     */
    public String generateResponse(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        try {
            String prompt = buildPrompt(userMessage, conversationHistory, scamType, threatLevel);

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
    private String buildPrompt(String userMessage, List<ChatRequest.ConversationMessage> conversationHistory,
            String scamType, double threatLevel) {
        StringBuilder prompt = new StringBuilder();

        // System persona from PersonaService
        prompt.append(personaService.getCurrentPersonaPrompt());

        // Dynamic context for Ollama
        if (threatLevel >= 0.6) {
            prompt.append("\n\nSYSTEM ALERT: This user is a suspected scammer (Type: ").append(scamType).append("). ")
                    .append("Be extra cautious. Stall them. Do NOT give money yet.");
        }

        prompt.append("\n\n");

        // Conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            prompt.append("Previous conversation:\n");
            for (ChatRequest.ConversationMessage msg : conversationHistory) {
                String role = "user".equals(msg.getSender()) ? "Them" : "You";
                prompt.append(role).append(": ").append(msg.getText()).append("\n");
            }
            prompt.append("\n");
        }

        // Current message
        prompt.append("Them: ").append(userMessage).append("\n");
        prompt.append("You: ");

        return prompt.toString();
    }

}
