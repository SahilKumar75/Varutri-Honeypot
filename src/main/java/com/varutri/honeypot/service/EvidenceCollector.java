package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.ChatRequest;
import com.varutri.honeypot.dto.ExtractedInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for collecting and storing evidence from scam conversations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceCollector {

    private final Map<String, EvidencePackage> evidenceStore = new ConcurrentHashMap<>();
    private final InformationExtractor informationExtractor;
    private final ScamDetector scamDetector;

    /**
     * Collect evidence from a conversation turn
     */
    public void collectEvidence(String sessionId, String userMessage, String assistantReply) {
        EvidencePackage evidence = evidenceStore.computeIfAbsent(sessionId, k -> new EvidencePackage(sessionId));

        // Add conversation turn
        ConversationTurn turn = new ConversationTurn();
        turn.setTimestamp(LocalDateTime.now());
        turn.setUserMessage(userMessage);
        turn.setAssistantReply(assistantReply);
        evidence.getConversation().add(turn);

        // Extract information from user message
        ExtractedInfo extracted = informationExtractor.extractInformation(userMessage);

        // Merge extracted info
        mergeExtractedInfo(evidence.getExtractedInfo(), extracted);

        // Update scam analysis
        String scamType = scamDetector.detectScamType(userMessage);
        double threatLevel = scamDetector.calculateThreatLevel(userMessage);
        List<String> keywords = scamDetector.extractSuspiciousKeywords(userMessage);

        if (!scamType.equals("UNKNOWN")) {
            evidence.setScamType(scamType);
        }
        evidence.setThreatLevel(Math.max(evidence.getThreatLevel(), threatLevel));
        evidence.getExtractedInfo().setSuspiciousKeywords(keywords);

        evidence.setLastUpdated(LocalDateTime.now());

        log.info("📊 Evidence collected for session {}: Threat={}, Type={}",
                sessionId, String.format("%.2f", threatLevel), scamType);
    }

    /**
     * Get evidence package for a session
     */
    public EvidencePackage getEvidence(String sessionId) {
        return evidenceStore.get(sessionId);
    }

    /**
     * Get all evidence packages
     */
    public List<EvidencePackage> getAllEvidence() {
        return new ArrayList<>(evidenceStore.values());
    }

    /**
     * Get high-threat evidence packages
     */
    public List<EvidencePackage> getHighThreatEvidence() {
        return evidenceStore.values().stream()
                .filter(e -> e.getThreatLevel() >= 0.6)
                .toList();
    }

    /**
     * Merge extracted information
     */
    private void mergeExtractedInfo(ExtractedInfo target, ExtractedInfo source) {
        source.getUpiIds().forEach(upi -> {
            if (!target.getUpiIds().contains(upi)) {
                target.getUpiIds().add(upi);
            }
        });
        source.getPhoneNumbers().forEach(phone -> {
            if (!target.getPhoneNumbers().contains(phone)) {
                target.getPhoneNumbers().add(phone);
            }
        });
        source.getBankAccountNumbers().forEach(acc -> {
            if (!target.getBankAccountNumbers().contains(acc)) {
                target.getBankAccountNumbers().add(acc);
            }
        });
        source.getIfscCodes().forEach(ifsc -> {
            if (!target.getIfscCodes().contains(ifsc)) {
                target.getIfscCodes().add(ifsc);
            }
        });
        source.getUrls().forEach(url -> {
            if (!target.getUrls().contains(url)) {
                target.getUrls().add(url);
            }
        });
        source.getEmails().forEach(email -> {
            if (!target.getEmails().contains(email)) {
                target.getEmails().add(email);
            }
        });
    }

    /**
     * Evidence package for law enforcement
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidencePackage {
        private String sessionId;
        private LocalDateTime firstContact;
        private LocalDateTime lastUpdated;
        private String scamType = "UNKNOWN";
        private double threatLevel = 0.0;
        private List<ConversationTurn> conversation = new ArrayList<>();
        private ExtractedInfo extractedInfo = new ExtractedInfo();

        public EvidencePackage(String sessionId) {
            this.sessionId = sessionId;
            this.firstContact = LocalDateTime.now();
            this.lastUpdated = LocalDateTime.now();
        }
    }

    /**
     * Single conversation turn
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationTurn {
        private LocalDateTime timestamp;
        private String userMessage;
        private String assistantReply;
    }
}
