package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for mapping the response from Hugging Face Inference API
 * The API returns a nested list of classification results
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhishingDetectionResponse {

    /**
     * Single classification result with label and score
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificationResult {
        @JsonProperty("label")
        private String label;

        @JsonProperty("score")
        private double score;
    }

    // The HF API returns [[{label, score}, {label, score}]]
    // This wrapper helps with parsing
    private List<List<ClassificationResult>> results;

    /**
     * Get the top result (highest confidence)
     */
    public ClassificationResult getTopResult() {
        if (results == null || results.isEmpty() || results.get(0).isEmpty()) {
            return null;
        }
        return results.get(0).stream()
                .max((a, b) -> Double.compare(a.getScore(), b.getScore()))
                .orElse(null);
    }

    /**
     * Check if the response indicates phishing
     */
    public boolean isPhishing() {
        ClassificationResult top = getTopResult();
        if (top == null)
            return false;

        String label = top.getLabel().toLowerCase();
        return (label.contains("phishing") ||
                label.equals("label_1") ||
                label.contains("spam") ||
                label.contains("malicious")) && top.getScore() > 0.5;
    }

    /**
     * Get confidence score for phishing detection
     */
    public double getPhishingConfidence() {
        ClassificationResult top = getTopResult();
        return top != null ? top.getScore() : 0.0;
    }
}
