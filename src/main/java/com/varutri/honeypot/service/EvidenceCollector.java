package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.ExtractedInfo;
import com.varutri.honeypot.entity.EvidenceEntity;
import com.varutri.honeypot.repository.EvidenceRepository;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for collecting and storing evidence from scam conversations
 * Now uses MongoDB for persistent storage with in-memory cache
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceCollector {

    private final EvidenceRepository evidenceRepository;
    private final InformationExtractor informationExtractor;
    private final ScamDetector scamDetector;

    // In-memory cache for fast access
    private final Map<String, EvidencePackage> evidenceCache = new ConcurrentHashMap<>();

    /**
     * Collect evidence from a conversation turn
     */
    public void collectEvidence(String sessionId, String userMessage, String assistantReply) {
        EvidencePackage evidence = getOrCreateEvidence(sessionId);

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

        // Persist to MongoDB
        persistEvidence(sessionId, evidence);

        log.info("📊 Evidence collected for session {}: Threat={}, Type={}",
                sessionId, String.format("%.2f", threatLevel), scamType);
    }

    /**
     * Get or create evidence package
     */
    private EvidencePackage getOrCreateEvidence(String sessionId) {
        // Check cache first
        EvidencePackage cached = evidenceCache.get(sessionId);
        if (cached != null) {
            return cached;
        }

        // Check MongoDB
        Optional<EvidenceEntity> existingEntity = evidenceRepository.findBySessionId(sessionId);
        if (existingEntity.isPresent()) {
            EvidencePackage evidence = entityToEvidencePackage(existingEntity.get());
            evidenceCache.put(sessionId, evidence);
            log.debug("Loaded evidence from MongoDB: {}", sessionId);
            return evidence;
        }

        // Create new evidence package
        EvidencePackage newEvidence = new EvidencePackage(sessionId);
        evidenceCache.put(sessionId, newEvidence);
        return newEvidence;
    }

    /**
     * Get evidence package for a session
     */
    public EvidencePackage getEvidence(String sessionId) {
        // Check cache first
        EvidencePackage cached = evidenceCache.get(sessionId);
        if (cached != null) {
            return cached;
        }

        // Check MongoDB
        Optional<EvidenceEntity> entity = evidenceRepository.findBySessionId(sessionId);
        if (entity.isPresent()) {
            EvidencePackage evidence = entityToEvidencePackage(entity.get());
            evidenceCache.put(sessionId, evidence);
            return evidence;
        }

        return null;
    }

    /**
     * Get all evidence packages (from MongoDB)
     */
    public List<EvidencePackage> getAllEvidence() {
        return evidenceRepository.findAll().stream()
                .map(this::entityToEvidencePackage)
                .toList();
    }

    /**
     * Get high-threat evidence packages (from MongoDB)
     */
    public List<EvidencePackage> getHighThreatEvidence() {
        return evidenceRepository.findByThreatLevelGreaterThanEqual(0.6).stream()
                .map(this::entityToEvidencePackage)
                .toList();
    }

    /**
     * Get total evidence count
     */
    public long getTotalEvidenceCount() {
        return evidenceRepository.count();
    }

    /**
     * Persist evidence to MongoDB
     */
    private void persistEvidence(String sessionId, EvidencePackage evidence) {
        try {
            EvidenceEntity entity = evidenceRepository.findBySessionId(sessionId)
                    .orElse(EvidenceEntity.createNew(sessionId));

            // Update entity fields
            entity.setScamType(evidence.getScamType());
            entity.setThreatLevel(evidence.getThreatLevel());
            entity.setLastUpdated(LocalDateTime.now());

            // Convert conversation turns
            List<EvidenceEntity.ConversationTurn> mongoTurns = evidence.getConversation().stream()
                    .map(turn -> EvidenceEntity.ConversationTurn.builder()
                            .timestamp(turn.getTimestamp())
                            .userMessage(turn.getUserMessage())
                            .assistantReply(turn.getAssistantReply())
                            .build())
                    .toList();
            entity.setConversation(mongoTurns);

            // Convert extracted info
            ExtractedInfo info = evidence.getExtractedInfo();
            EvidenceEntity.ExtractedIntelligence mongoInfo = EvidenceEntity.ExtractedIntelligence.builder()
                    .upiIds(new ArrayList<>(info.getUpiIds()))
                    .bankAccountNumbers(new ArrayList<>(info.getBankAccountNumbers()))
                    .ifscCodes(new ArrayList<>(info.getIfscCodes()))
                    .phoneNumbers(new ArrayList<>(info.getPhoneNumbers()))
                    .urls(new ArrayList<>(info.getUrls()))
                    .emails(new ArrayList<>(info.getEmails()))
                    .suspiciousKeywords(new ArrayList<>(info.getSuspiciousKeywords()))
                    .build();
            entity.setExtractedInfo(mongoInfo);

            evidenceRepository.save(entity);
            log.debug("Persisted evidence to MongoDB: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to persist evidence to MongoDB {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Convert MongoDB entity to EvidencePackage
     */
    private EvidencePackage entityToEvidencePackage(EvidenceEntity entity) {
        EvidencePackage evidence = new EvidencePackage(entity.getSessionId());
        evidence.setFirstContact(entity.getFirstContact());
        evidence.setLastUpdated(entity.getLastUpdated());
        evidence.setScamType(entity.getScamType());
        evidence.setThreatLevel(entity.getThreatLevel());

        // Convert conversation turns
        if (entity.getConversation() != null) {
            List<ConversationTurn> turns = entity.getConversation().stream()
                    .map(turn -> {
                        ConversationTurn ct = new ConversationTurn();
                        ct.setTimestamp(turn.getTimestamp());
                        ct.setUserMessage(turn.getUserMessage());
                        ct.setAssistantReply(turn.getAssistantReply());
                        return ct;
                    })
                    .toList();
            evidence.setConversation(new ArrayList<>(turns));
        }

        // Convert extracted info
        if (entity.getExtractedInfo() != null) {
            EvidenceEntity.ExtractedIntelligence mongoInfo = entity.getExtractedInfo();
            ExtractedInfo info = new ExtractedInfo();
            info.setUpiIds(new ArrayList<>(mongoInfo.getUpiIds()));
            info.setBankAccountNumbers(new ArrayList<>(mongoInfo.getBankAccountNumbers()));
            info.setIfscCodes(new ArrayList<>(mongoInfo.getIfscCodes()));
            info.setPhoneNumbers(new ArrayList<>(mongoInfo.getPhoneNumbers()));
            info.setUrls(new ArrayList<>(mongoInfo.getUrls()));
            info.setEmails(new ArrayList<>(mongoInfo.getEmails()));
            info.setSuspiciousKeywords(new ArrayList<>(mongoInfo.getSuspiciousKeywords()));
            evidence.setExtractedInfo(info);
        }

        return evidence;
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
