package com.varutri.honeypot.service.core;

import com.varutri.honeypot.dto.ConsumerAnalysisResponse;
import com.varutri.honeypot.dto.ConsumerSignalRequest;
import com.varutri.honeypot.dto.ExtractedInfo;
import com.varutri.honeypot.dto.ThreatAssessmentResponse;
import com.varutri.honeypot.service.ai.EnsembleThreatScorer;
import com.varutri.honeypot.service.ai.InformationExtractor;
import com.varutri.honeypot.service.data.EvidenceCollector;
import com.varutri.honeypot.service.data.SessionStore;
import com.varutri.honeypot.service.security.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates channel-aware consumer signal analysis for mobile and extension clients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerSignalService {

    private final EnsembleThreatScorer ensembleThreatScorer;
    private final InformationExtractor informationExtractor;
    private final SessionStore sessionStore;
    private final EvidenceCollector evidenceCollector;
    private final InputSanitizer inputSanitizer;
    private final ConsumerCacheService consumerCacheService;

    public ConsumerAnalysisResponse analyzeSignal(ConsumerSignalRequest request) {
        String sessionId = resolveSessionId(request.getSessionId(), request.getChannel());
        String analyzableText = buildAnalyzableText(request);

        String sanitizedText = inputSanitizer.sanitizeMessageText(analyzableText);
        if (sanitizedText == null || sanitizedText.isBlank()) {
            throw new IllegalArgumentException("No analyzable text could be derived from payload");
        }

        log.info("Consumer signal analysis started: session={}, channel={}, contentLength={}",
                sessionId, request.getChannel(), sanitizedText.length());

        if (inputSanitizer.containsPromptInjection(sanitizedText)) {
            log.warn("Potential prompt-injection style content detected in consumer signal: {}",
                    inputSanitizer.sanitizeForLogging(sessionId));
        }

        EnsembleThreatScorer.ThreatAssessment assessment =
                ensembleThreatScorer.assessThreat(sanitizedText, new ArrayList<>());

        ExtractedInfo extractedInfo = informationExtractor.extractInformation(sanitizedText);

        persistAnalysisArtifacts(sessionId, sanitizedText, assessment);

        ConsumerAnalysisResponse.Verdict verdict = mapVerdict(assessment.getThreatLevel());
        List<String> recommendedActions = buildRecommendedActions(request.getChannel(), verdict, extractedInfo);
        List<String> platformNotes = buildPlatformNotes(request.getMetadata(), request.getChannel());

        log.info("Consumer signal analyzed: session={}, verdict={}, threatLevel={}, score={}",
                sessionId, verdict, assessment.getThreatLevel(), assessment.getEnsembleScore());

        return ConsumerAnalysisResponse.builder()
                .sessionId(sessionId)
                .analysisTimestamp(Instant.now().toString())
                .channel(request.getChannel().name())
                .verdict(verdict)
                .reportRecommended(assessment.isHighThreat())
                .threatAssessment(ThreatAssessmentResponse.fromAssessment(assessment))
                .extractedInfo(extractedInfo)
                .recommendedActions(recommendedActions)
                .platformNotes(platformNotes)
                .analyzedTextPreview(buildPreview(sanitizedText))
                .build();
    }

    private String resolveSessionId(String incomingSessionId, ConsumerSignalRequest.Channel channel) {
        if (incomingSessionId != null && !incomingSessionId.isBlank()) {
            return inputSanitizer.sanitizeSessionId(incomingSessionId);
        }
        String generated = "consumer-" + channel.name().toLowerCase() + "-" + System.currentTimeMillis();
        return inputSanitizer.sanitizeSessionId(generated);
    }

    private String buildAnalyzableText(ConsumerSignalRequest request) {
        ConsumerSignalRequest.SignalPayload payload = request.getPayload();
        StringBuilder text = new StringBuilder();

        text.append("channel: ").append(request.getChannel().name()).append("\n");

        appendIfPresent(text, "text", payload.getText());
        appendIfPresent(text, "callTranscript", payload.getCallTranscript());
        appendIfPresent(text, "emailSubject", payload.getEmailSubject());
        appendIfPresent(text, "senderId", payload.getSenderId());
        appendIfPresent(text, "url", payload.getUrl());
        appendIfPresent(text, "additionalContext", payload.getAdditionalContext());

        if (request.getMetadata() != null) {
            appendIfPresent(text, "platform", request.getMetadata().getPlatform() != null
                    ? request.getMetadata().getPlatform().name()
                    : null);
            appendIfPresent(text, "sourceApp", request.getMetadata().getSourceApp());
            appendIfPresent(text, "locale", request.getMetadata().getLocale());
        }

        return text.toString();
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(": ").append(value.trim()).append("\n");
        }
    }

    private void persistAnalysisArtifacts(String sessionId,
            String analyzableText,
            EnsembleThreatScorer.ThreatAssessment assessment) {
        try {
            sessionStore.addMessage(sessionId, "user", analyzableText);
            sessionStore.addMessage(sessionId, "assistant", createSystemSummary(assessment));
            evidenceCollector.collectEvidence(sessionId, analyzableText, createSystemSummary(assessment));
            consumerCacheService.invalidateHistory(sessionId);
        } catch (Exception ex) {
            log.warn("Failed to persist consumer analysis artifacts for {}: {}",
                    inputSanitizer.sanitizeForLogging(sessionId), ex.getMessage());
        }
    }

    private String createSystemSummary(EnsembleThreatScorer.ThreatAssessment assessment) {
        return String.format("Consumer signal classified as %s (%s, %.0f%% confidence)",
                assessment.getThreatLevel(),
                assessment.getPrimaryScamType(),
                assessment.getCalibratedConfidence() * 100);
    }

    private ConsumerAnalysisResponse.Verdict mapVerdict(String threatLevel) {
        if (threatLevel == null) {
            return ConsumerAnalysisResponse.Verdict.SUSPICIOUS;
        }
        return switch (threatLevel) {
            case "HIGH", "CRITICAL" -> ConsumerAnalysisResponse.Verdict.DANGER;
            case "SAFE" -> ConsumerAnalysisResponse.Verdict.SAFE;
            default -> ConsumerAnalysisResponse.Verdict.SUSPICIOUS;
        };
    }

    private List<String> buildRecommendedActions(ConsumerSignalRequest.Channel channel,
            ConsumerAnalysisResponse.Verdict verdict,
            ExtractedInfo extractedInfo) {
        List<String> actions = new ArrayList<>();

        switch (verdict) {
            case DANGER -> {
                actions.add("Do not reply, pay, or share OTP/PIN/card details.");
                actions.add("Block the sender and preserve screenshots for reporting.");
                actions.add("Report urgently to cybercrime helpline/portal in your region.");
            }
            case SUSPICIOUS -> {
                actions.add("Pause and verify through official channels before taking action.");
                actions.add("Do not click links or install apps shared by unknown contacts.");
            }
            case SAFE -> actions.add("No clear scam signal detected, but continue normal caution.");
        }

        if (extractedInfo != null && extractedInfo.getUrls() != null && !extractedInfo.getUrls().isEmpty()) {
            actions.add("Detected URL indicators: avoid opening links until verified.");
        }
        if (channel == ConsumerSignalRequest.Channel.CALL) {
            actions.add("Never install remote-control apps requested during a call.");
        }
        if (channel == ConsumerSignalRequest.Channel.EMAIL) {
            actions.add("Verify sender domain and watch for lookalike addresses.");
        }
        if (channel == ConsumerSignalRequest.Channel.WHATSAPP) {
            actions.add("Forward suspicious chat to your verified protection number for follow-up.");
        }
        if (channel == ConsumerSignalRequest.Channel.PROMPT) {
            actions.add("Do not execute untrusted prompts or shell commands without review.");
        }

        return actions;
    }

    private List<String> buildPlatformNotes(ConsumerSignalRequest.Metadata metadata,
            ConsumerSignalRequest.Channel channel) {
        List<String> notes = new ArrayList<>();

        if (metadata == null || metadata.getPlatform() == null) {
            return notes;
        }

        if (metadata.getPlatform() == ConsumerSignalRequest.Platform.IOS
                && channel == ConsumerSignalRequest.Channel.SMS) {
            notes.add("iOS apps cannot read full SMS inbox. Analysis uses shared text or filter-extension context.");
        }

        if (metadata.getPlatform() == ConsumerSignalRequest.Platform.IOS
                && channel == ConsumerSignalRequest.Channel.CALL) {
            notes.add("iOS call protection is caller-ID/block-list based, not full call-log ingestion.");
        }

        if (metadata.getPlatform() == ConsumerSignalRequest.Platform.ANDROID
                && (channel == ConsumerSignalRequest.Channel.SMS || channel == ConsumerSignalRequest.Channel.CALL)) {
            notes.add("Android deep SMS/Call automation may require default-handler role and policy-compliant permission declarations.");
        }

        return notes;
    }

    private String buildPreview(String text) {
        String preview = text.replace('\n', ' ').trim();
        if (preview.length() > 220) {
            return preview.substring(0, 220) + "...";
        }
        return preview;
    }
}