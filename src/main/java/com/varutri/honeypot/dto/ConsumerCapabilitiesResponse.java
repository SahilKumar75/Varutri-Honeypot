package com.varutri.honeypot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform capability matrix for consumer clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerCapabilitiesResponse {

    private String platform;
    private String generatedAt;

    @Builder.Default
    private List<ChannelCapability> channels = new ArrayList<>();

    @Builder.Default
    private List<String> globalLimitations = new ArrayList<>();

    @Builder.Default
    private List<String> operationalNotes = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelCapability {
        private String channel;
        private AutomationLevel automationLevel;
        private boolean passiveDetection;
        private boolean userShareRequired;

        @Builder.Default
        private List<String> limitations = new ArrayList<>();

        @Builder.Default
        private List<String> recommendedFlows = new ArrayList<>();
    }

    public enum AutomationLevel {
        FULL,
        PARTIAL,
        MANUAL_ONLY
    }
}