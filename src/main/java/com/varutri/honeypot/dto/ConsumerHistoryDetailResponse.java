package com.varutri.honeypot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed consumer session history view for app detail pages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerHistoryDetailResponse {
    private String sessionId;
    private String channel;
    private ConsumerAnalysisResponse.Verdict verdict;
    private String threatLevel;
    private double threatScore;
    private String firstSeen;
    private String lastUpdated;
    private ExtractedInfo extractedInfo;

    @Builder.Default
    private List<ConversationEntry> conversation = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationEntry {
        private String sender;
        private String text;
        private Long timestamp;
    }
}