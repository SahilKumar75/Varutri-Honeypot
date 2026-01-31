package com.varutri.honeypot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for detecting scam patterns and calculating threat levels
 */
@Slf4j
@Service
public class ScamDetector {

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
     */
    public String detectScamType(String message) {
        String lowerMessage = message.toLowerCase();

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
     * Calculate threat level (0.0 to 1.0)
     */
    public double calculateThreatLevel(String message) {
        String lowerMessage = message.toLowerCase();
        double threatLevel = 0.0;

        // Base threat from scam type
        String scamType = detectScamType(message);
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
}
