package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Incoming chat request from the scammer/client
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Session ID is required")
    @JsonProperty("sessionId")
    private String sessionId;

    @NotBlank(message = "Message is required")
    @JsonProperty("message")
    private String message;

    @NotNull(message = "Conversation history is required")
    @JsonProperty("conversationHistory")
    private List<ConversationMessage> conversationHistory;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        @JsonProperty("role")
        private String role; // "user" or "assistant"

        @JsonProperty("content")
        private String content;
    }
}
