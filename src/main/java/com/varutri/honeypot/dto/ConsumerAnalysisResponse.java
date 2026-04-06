package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumer-friendly analysis response for mobile and extension clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsumerAnalysisResponse {

    private String sessionId;
    private String analysisTimestamp;
    private String channel;
    private Verdict verdict;
    private boolean reportRecommended;

    private ThreatAssessmentResponse threatAssessment;
    private ExtractedInfo extractedInfo;

    @Builder.Default
    private List<String> recommendedActions = new ArrayList<>();

    @Builder.Default
    private List<String> platformNotes = new ArrayList<>();

    private String analyzedTextPreview;

    public enum Verdict {
        SAFE,
        SUSPICIOUS,
        DANGER
    }
}