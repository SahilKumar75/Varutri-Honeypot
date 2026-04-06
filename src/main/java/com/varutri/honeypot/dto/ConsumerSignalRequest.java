package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Channel-aware consumer signal request for mobile apps, browser extensions,
 * and share flows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerSignalRequest {

    @Size(min = 1, max = 100, message = "Session ID must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Session ID can only contain letters, numbers, underscores, and dashes")
    private String sessionId;

    @NotNull(message = "Channel is required")
    private Channel channel;

    @Valid
    @NotNull(message = "Payload is required")
    private SignalPayload payload;

    @Valid
    private Metadata metadata;

    @AssertTrue(message = "At least one analyzable field is required")
    public boolean hasAnalyzablePayload() {
        if (payload == null) {
            return false;
        }
        return hasText(payload.text)
                || hasText(payload.callTranscript)
                || hasText(payload.emailSubject)
                || hasText(payload.senderId)
                || hasText(payload.url)
                || hasText(payload.additionalContext);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalPayload {

        @Size(max = 5000, message = "Text cannot exceed 5000 characters")
        private String text;

        @Size(max = 5000, message = "Call transcript cannot exceed 5000 characters")
        private String callTranscript;

        @Size(max = 500, message = "Email subject cannot exceed 500 characters")
        private String emailSubject;

        @Size(max = 256, message = "Sender ID cannot exceed 256 characters")
        private String senderId;

        @Size(max = 2048, message = "URL cannot exceed 2048 characters")
        private String url;

        @Size(max = 2000, message = "Additional context cannot exceed 2000 characters")
        private String additionalContext;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {

        private Platform platform;

        @Size(max = 100, message = "Source app cannot exceed 100 characters")
        private String sourceApp;

        @Size(max = 10, message = "Locale cannot exceed 10 characters")
        @Pattern(regexp = "^[a-zA-Z_-]*$", message = "Locale contains invalid characters")
        private String locale;

        @Size(max = 100, message = "Device model cannot exceed 100 characters")
        private String deviceModel;
    }

    public enum Channel {
        SMS,
        CALL,
        WHATSAPP,
        EMAIL,
        BROWSER,
        MANUAL,
        PROMPT,
        OTHER;

        @JsonCreator
        public static Channel fromValue(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Channel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toValue() {
            return name();
        }
    }

    public enum Platform {
        ANDROID,
        IOS,
        WEB,
        EXTENSION,
        DESKTOP,
        OTHER;

        @JsonCreator
        public static Platform fromValue(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Platform.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toValue() {
            return name();
        }
    }
}