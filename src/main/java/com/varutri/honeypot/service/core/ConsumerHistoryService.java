package com.varutri.honeypot.service.core;

import com.varutri.honeypot.dto.ConsumerAnalysisResponse;
import com.varutri.honeypot.dto.ConsumerHistoryDetailResponse;
import com.varutri.honeypot.dto.ConsumerHistoryItemResponse;
import com.varutri.honeypot.dto.ExtractedInfo;
import com.varutri.honeypot.entity.EvidenceEntity;
import com.varutri.honeypot.entity.SessionEntity;
import com.varutri.honeypot.exception.ResourceNotFoundException;
import com.varutri.honeypot.repository.EvidenceRepository;
import com.varutri.honeypot.repository.SessionRepository;
import com.varutri.honeypot.service.security.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Provides consumer session history list and detail views.
 */
@Service
@RequiredArgsConstructor
public class ConsumerHistoryService {

    private final SessionRepository sessionRepository;
    private final EvidenceRepository evidenceRepository;
    private final InputSanitizer inputSanitizer;
    private final ConsumerCacheService consumerCacheService;

    public List<ConsumerHistoryItemResponse> getRecentHistory(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));

        return consumerCacheService.getOrLoadHistoryList(limit, () -> loadRecentHistory(limit));
    }

    private List<ConsumerHistoryItemResponse> loadRecentHistory(int limit) {
        return sessionRepository.findAll().stream()
                .filter(this::isConsumerSession)
                .sorted(Comparator.comparing(SessionEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .map(this::toHistoryItem)
                .toList();
    }

    public ConsumerHistoryDetailResponse getHistoryDetail(String sessionId) {
        String safeSessionId = inputSanitizer.sanitizeSessionId(sessionId);

        return consumerCacheService.getOrLoadHistoryDetail(safeSessionId, () -> loadHistoryDetail(safeSessionId));
    }

    private ConsumerHistoryDetailResponse loadHistoryDetail(String safeSessionId) {

        SessionEntity session = sessionRepository.findBySessionId(safeSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer session", safeSessionId));

        if (!isConsumerSession(session)) {
            throw new ResourceNotFoundException("Consumer session", safeSessionId);
        }

        Optional<EvidenceEntity> evidenceOpt = evidenceRepository.findBySessionId(safeSessionId);
        EvidenceEntity evidence = evidenceOpt.orElse(null);

        double threatScore = evidence != null ? evidence.getThreatLevel() : 0.0;
        String threatLevel = deriveThreatLevel(threatScore);
        ConsumerAnalysisResponse.Verdict verdict = mapVerdict(threatScore);

        List<ConsumerHistoryDetailResponse.ConversationEntry> conversation = new ArrayList<>();
        if (session.getConversationHistory() != null) {
            conversation = session.getConversationHistory().stream()
                    .map(msg -> ConsumerHistoryDetailResponse.ConversationEntry.builder()
                            .sender(msg.getSender())
                            .text(msg.getText())
                            .timestamp(msg.getTimestamp())
                            .build())
                    .toList();
        }

        return ConsumerHistoryDetailResponse.builder()
                .sessionId(safeSessionId)
                .channel(detectChannel(session))
                .verdict(verdict)
                .threatLevel(threatLevel)
                .threatScore(Math.round(threatScore * 100.0) / 100.0)
                .firstSeen(session.getCreatedAt() != null ? session.getCreatedAt().toString() : null)
                .lastUpdated(session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null)
                .extractedInfo(mapExtractedInfo(evidence))
                .conversation(conversation)
                .build();
    }

    private ConsumerHistoryItemResponse toHistoryItem(SessionEntity session) {
        Optional<EvidenceEntity> evidenceOpt = evidenceRepository.findBySessionId(session.getSessionId());
        EvidenceEntity evidence = evidenceOpt.orElse(null);

        double threatScore = evidence != null ? evidence.getThreatLevel() : 0.0;
        String threatLevel = deriveThreatLevel(threatScore);
        ConsumerAnalysisResponse.Verdict verdict = mapVerdict(threatScore);

        return ConsumerHistoryItemResponse.builder()
                .sessionId(session.getSessionId())
                .channel(detectChannel(session))
                .verdict(verdict)
                .threatLevel(threatLevel)
                .threatScore(Math.round(threatScore * 100.0) / 100.0)
                .lastUpdated(session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null)
                .messageCount(session.getConversationHistory() != null ? session.getConversationHistory().size() : 0)
                .indicatorCount(countIndicators(evidence))
                .lastMessagePreview(getLastMessagePreview(session))
                .build();
    }

    private boolean isConsumerSession(SessionEntity session) {
        if (session == null || session.getConversationHistory() == null || session.getConversationHistory().isEmpty()) {
            return false;
        }

        if (session.getSessionId() != null && session.getSessionId().startsWith("consumer-")) {
            return true;
        }

        SessionEntity.ConversationMessage firstMessage = session.getConversationHistory().get(0);
        if (firstMessage == null || firstMessage.getText() == null) {
            return false;
        }

        return firstMessage.getText().toLowerCase(Locale.ROOT).startsWith("channel:");
    }

    private String detectChannel(SessionEntity session) {
        if (session.getConversationHistory() == null || session.getConversationHistory().isEmpty()) {
            return "UNKNOWN";
        }

        String text = session.getConversationHistory().get(0).getText();
        if (text == null) {
            return "UNKNOWN";
        }

        String lower = text.toLowerCase(Locale.ROOT);
        String prefix = "channel:";
        int idx = lower.indexOf(prefix);
        if (idx < 0) {
            return "UNKNOWN";
        }

        String candidate = text.substring(idx + prefix.length()).trim();
        int newlineIndex = candidate.indexOf('\n');
        if (newlineIndex >= 0) {
            candidate = candidate.substring(0, newlineIndex);
        }

        candidate = candidate.trim();
        return candidate.isBlank() ? "UNKNOWN" : candidate.toUpperCase(Locale.ROOT);
    }

    private String getLastMessagePreview(SessionEntity session) {
        if (session.getConversationHistory() == null || session.getConversationHistory().isEmpty()) {
            return null;
        }

        SessionEntity.ConversationMessage last =
                session.getConversationHistory().get(session.getConversationHistory().size() - 1);
        if (last == null || last.getText() == null) {
            return null;
        }

        String preview = last.getText().replace('\n', ' ').trim();
        if (preview.length() > 140) {
            return preview.substring(0, 140) + "...";
        }
        return preview;
    }

    private int countIndicators(EvidenceEntity evidence) {
        if (evidence == null || evidence.getExtractedInfo() == null) {
            return 0;
        }

        EvidenceEntity.ExtractedIntelligence info = evidence.getExtractedInfo();
        return sizeOf(info.getUpiIds())
                + sizeOf(info.getBankAccountNumbers())
                + sizeOf(info.getPhoneNumbers())
                + sizeOf(info.getUrls())
                + sizeOf(info.getEmails());
    }

    private int sizeOf(List<String> list) {
        return list == null ? 0 : list.size();
    }

    private String deriveThreatLevel(double threatScore) {
        if (threatScore >= 0.85) {
            return "CRITICAL";
        }
        if (threatScore >= 0.70) {
            return "HIGH";
        }
        if (threatScore >= 0.40) {
            return "MEDIUM";
        }
        if (threatScore >= 0.20) {
            return "LOW";
        }
        return "SAFE";
    }

    private ConsumerAnalysisResponse.Verdict mapVerdict(double threatScore) {
        if (threatScore >= 0.70) {
            return ConsumerAnalysisResponse.Verdict.DANGER;
        }
        if (threatScore >= 0.30) {
            return ConsumerAnalysisResponse.Verdict.SUSPICIOUS;
        }
        return ConsumerAnalysisResponse.Verdict.SAFE;
    }

    private ExtractedInfo mapExtractedInfo(EvidenceEntity evidence) {
        if (evidence == null || evidence.getExtractedInfo() == null) {
            return new ExtractedInfo();
        }

        EvidenceEntity.ExtractedIntelligence src = evidence.getExtractedInfo();
        ExtractedInfo out = new ExtractedInfo();
        out.setUpiIds(src.getUpiIds() != null ? new ArrayList<>(src.getUpiIds()) : new ArrayList<>());
        out.setBankAccountNumbers(src.getBankAccountNumbers() != null
                ? new ArrayList<>(src.getBankAccountNumbers())
                : new ArrayList<>());
        out.setIfscCodes(src.getIfscCodes() != null ? new ArrayList<>(src.getIfscCodes()) : new ArrayList<>());
        out.setPhoneNumbers(src.getPhoneNumbers() != null ? new ArrayList<>(src.getPhoneNumbers()) : new ArrayList<>());
        out.setUrls(src.getUrls() != null ? new ArrayList<>(src.getUrls()) : new ArrayList<>());
        out.setEmails(src.getEmails() != null ? new ArrayList<>(src.getEmails()) : new ArrayList<>());
        out.setSuspiciousKeywords(src.getSuspiciousKeywords() != null
                ? new ArrayList<>(src.getSuspiciousKeywords())
                : new ArrayList<>());
        return out;
    }
}