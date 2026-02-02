package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.PhishingDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for detecting scam patterns and calculating threat levels
 * Combines regex-based detection with AI-powered phishing detection
 */
@Slf4j
@Service
public class ScamDetector {

    // Optional HuggingFace service for AI-powered phishing detection
    @Autowired(required = false)
    private HuggingFaceService huggingFaceService;

    // Cache for AI detection result to avoid duplicate calls
    private final ThreadLocal<PhishingDetectionResult> cachedAiResult = new ThreadLocal<>();

    // Scam type keywords
    private static final List<String> INVESTMENT_SCAM_KEYWORDS = Arrays.asList(
            "investment", "returns", "profit", "earn money", "guaranteed", "double your money",
            "stock market", "trading", "crypto", "bitcoin", "forex");

    private static final List<String> LOTTERY_SCAM_KEYWORDS = Arrays.asList(
            "lottery", "won", "prize", "winner", "congratulations", "claim", "lucky draw");

    private static final List<String> TECH_SUPPORT_SCAM_KEYWORDS = Arrays.asList(
            "virus", "infected", "security alert", "microsoft", "tech support", "computer problem",
            "refund", "subscription", "renewal");

    private static final List<String> PHISHING_KEYWORDS = Arrays.asList(
            "verify account", "update details", "confirm identity", "suspended account",
            "click here", "urgent action", "limited time", "expire");

    private static final List<String> JOB_SCAM_KEYWORDS = Arrays.asList(
            "work from home", "part time job", "easy money", "no experience", "registration fee",
            "joining fee", "training fee");

    private static final List<String> URGENCY_KEYWORDS = Arrays.asList(
            "urgent", "immediately", "now", "today", "limited time", "hurry", "act fast",
            "don't miss", "last chance", "expires");

    private static final List<String> PAYMENT_REQUEST_KEYWORDS = Arrays.asList(
            "send money", "transfer", "payment", "upi", "bank account", "ifsc", "account number",
            "paytm", "phonepe", "googlepay", "deposit");

    /**
     * Detect scam type based on message content
     * Uses both regex patterns and AI model for comprehensive detection
     */
    public String detectScamType(String message) {
        String lowerMessage = message.toLowerCase();

        // First, try regex-based detection
        String regexResult = detectScamTypeByRegex(lowerMessage);

        // If regex found a specific type, return it
        if (!regexResult.equals("UNKNOWN")) {
            return regexResult;
        }

        // If regex didn't find anything, try AI model
        PhishingDetectionResult aiResult = getAiPhishingResult(message);
        if (aiResult != null && aiResult.isPhishing() && aiResult.getConfidence() > 0.7) {
            log.info("AI model detected phishing with confidence: {}",
                    String.format("%.2f", aiResult.getConfidence()));
            return "PHISHING_AI_DETECTED";
        }

        return "UNKNOWN";
    }

    /**
     * Regex-based scam type detection
     */
    private String detectScamTypeByRegex(String lowerMessage) {
        if (containsKeywords(lowerMessage, INVESTMENT_SCAM_KEYWORDS)) {
            return "INVESTMENT_SCAM";
        } else if (containsKeywords(lowerMessage, LOTTERY_SCAM_KEYWORDS)) {
            return "LOTTERY_SCAM";
        } else if (containsKeywords(lowerMessage, TECH_SUPPORT_SCAM_KEYWORDS)) {
            return "TECH_SUPPORT_SCAM";
        } else if (containsKeywords(lowerMessage, PHISHING_KEYWORDS)) {
            return "PHISHING";
        } else if (containsKeywords(lowerMessage, JOB_SCAM_KEYWORDS)) {
            return "JOB_SCAM";
        }
        return "UNKNOWN";
    }

    /**
     * Get AI phishing detection result (cached per request)
     */
    private PhishingDetectionResult getAiPhishingResult(String message) {
        // Check if we have a cached result
        PhishingDetectionResult cached = cachedAiResult.get();
        if (cached != null) {
            return cached;
        }

        // Call AI service if available
        if (huggingFaceService != null) {
            try {
                PhishingDetectionResult result = huggingFaceService.detectPhishing(message);
                cachedAiResult.set(result);
                return result;
            } catch (Exception e) {
                log.warn("AI phishing detection failed, falling back to regex only: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Clear the cached AI result (call after processing a message)
     */
    public void clearCache() {
        cachedAiResult.remove();
    }

    /**
     * Calculate threat level (0.0 to 1.0)
     * Combines regex scoring with AI model confidence
     */
    public double calculateThreatLevel(String message) {
        String lowerMessage = message.toLowerCase();
        double threatLevel = 0.0;

        // Base threat from regex-based scam type detection
        String scamType = detectScamTypeByRegex(lowerMessage);
        if (!scamType.equals("UNKNOWN")) {
            threatLevel += 0.3;
        }

        // Urgency indicators
        if (containsKeywords(lowerMessage, URGENCY_KEYWORDS)) {
            threatLevel += 0.2;
        }

        // Payment requests
        if (containsKeywords(lowerMessage, PAYMENT_REQUEST_KEYWORDS)) {
            threatLevel += 0.3;
        }

        // Multiple suspicious patterns
        int patternCount = 0;
        if (containsKeywords(lowerMessage, INVESTMENT_SCAM_KEYWORDS))
            patternCount++;
        if (containsKeywords(lowerMessage, LOTTERY_SCAM_KEYWORDS))
            patternCount++;
        if (containsKeywords(lowerMessage, TECH_SUPPORT_SCAM_KEYWORDS))
            patternCount++;
        if (containsKeywords(lowerMessage, PHISHING_KEYWORDS))
            patternCount++;
        if (containsKeywords(lowerMessage, JOB_SCAM_KEYWORDS))
            patternCount++;

        if (patternCount >= 2) {
            threatLevel += 0.2;
        }

        // AI model contribution
        PhishingDetectionResult aiResult = getAiPhishingResult(message);
        if (aiResult != null && aiResult.isPhishing()) {
            // Add AI confidence to threat level (weighted at 0.3)
            double aiContribution = aiResult.getConfidence() * 0.3;
            threatLevel += aiContribution;
            log.debug("AI phishing detection added {} to threat level", String.format("%.2f", aiContribution));
        }

        // Clear cache after calculating
        clearCache();

        return Math.min(threatLevel, 1.0);
    }

    /**
     * Extract suspicious keywords found in message
     */
    public List<String> extractSuspiciousKeywords(String message) {
        String lowerMessage = message.toLowerCase();
        List<String> found = new ArrayList<>();

        List<List<String>> allKeywordLists = Arrays.asList(
                INVESTMENT_SCAM_KEYWORDS,
                LOTTERY_SCAM_KEYWORDS,
                TECH_SUPPORT_SCAM_KEYWORDS,
                PHISHING_KEYWORDS,
                JOB_SCAM_KEYWORDS,
                URGENCY_KEYWORDS,
                PAYMENT_REQUEST_KEYWORDS);

        for (List<String> keywords : allKeywordLists) {
            for (String keyword : keywords) {
                if (lowerMessage.contains(keyword) && !found.contains(keyword)) {
                    found.add(keyword);
                }
            }
        }

        // Add AI detection indicator if applicable
        PhishingDetectionResult aiResult = getAiPhishingResult(message);
        if (aiResult != null && aiResult.isPhishing() && aiResult.getConfidence() > 0.7) {
            found.add("[AI_PHISHING_DETECTED:" + String.format("%.2f", aiResult.getConfidence()) + "]");
        }

        return found;
    }

    /**
     * Check if message contains any keywords from the list
     */
    private boolean containsKeywords(String message, List<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }

    /**
     * Determine if threat level warrants an alert
     */
    public boolean shouldTriggerAlert(double threatLevel) {
        return threatLevel >= 0.6; // Alert if 60% or higher threat
    }

    /**
     * Check if AI phishing detection is available
     */
    public boolean isAiDetectionAvailable() {
        return huggingFaceService != null;
    }
}
