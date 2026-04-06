package com.varutri.honeypot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary list item for consumer analysis history timeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerHistoryItemResponse {
    private String sessionId;
    private String channel;
    private ConsumerAnalysisResponse.Verdict verdict;
    private String threatLevel;
    private double threatScore;
    private String lastUpdated;
    private int messageCount;
    private int indicatorCount;
    private String lastMessagePreview;
}