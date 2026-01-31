package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Incoming chat request from GUVI Hackathon platform
 * Matches official problem statement format
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Session ID is required")
    @JsonProperty("sessionId")
    private String sessionId;

    @NotNull(message = "Message is required")
    @JsonProperty("message")
    private Message message;

    @JsonProperty("conversationHistory")
    private List<ConversationMessage> conversationHistory;

    @JsonProperty("metadata")
    private Metadata metadata;

    /**
     * Message object as per GUVI format
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        @JsonProperty("sender")
        private String sender; // "scammer" or "user"

        @JsonProperty("text")
        private String text;

        @JsonProperty("timestamp")
        private String timestamp; // ISO-8601 format
    }

    /**
     * Conversation history message format
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        @JsonProperty("sender")
        private String sender; // "scammer" or "user"

        @JsonProperty("text")
        private String text;

        @JsonProperty("timestamp")
        private String timestamp;
    }

    /**
     * Optional metadata
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        @JsonProperty("channel")
        private String channel; // SMS / WhatsApp / Email / Chat

        @JsonProperty("language")
        private String language;

        @JsonProperty("locale")
        private String locale;
    }
}
