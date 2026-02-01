package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.ExtractedInfo;
import com.varutri.honeypot.dto.ScamReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for reporting scams to government authorities
 */
@Slf4j
@Service
public class GovernmentReportService {

    @Autowired
    private EvidenceCollector evidenceCollector;

    @Value("${government.report.auto-threshold:0.7}")
    private double autoReportThreshold;

    @Value("${government.report.email:report@cybercrime.gov.in}")
    private String reportEmail;

    // In-memory report storage (in production, use database)
    private final Map<String, ScamReport> reportArchive = new HashMap<>();

    /**
     * Check if session should trigger automatic report
     */
    public boolean shouldAutoReport(String sessionId) {
        EvidenceCollector.EvidencePackage evidence = evidenceCollector.getEvidence(sessionId);
        if (evidence == null) {
            return false;
        }

        double threatLevel = evidence.getThreatLevel();
        boolean highThreat = threatLevel >= autoReportThreshold;

        if (highThreat) {
            log.warn("🚨 High threat detected ({}): Auto-reporting session {}",
                    String.format("%.2f", threatLevel), sessionId);
        }

        return highThreat;
    }

    /**
     * Generate comprehensive scam report
     */
    public ScamReport generateReport(String sessionId) {
        EvidenceCollector.EvidencePackage evidence = evidenceCollector.getEvidence(sessionId);
        if (evidence == null) {
            log.error("No evidence found for session {}", sessionId);
            return null;
        }

        ExtractedInfo info = evidence.getExtractedInfo();

        // Convert conversation to report format
        List<ScamReport.ConversationTurn> conversation = evidence.getConversation().stream()
                .map(turn -> ScamReport.ConversationTurn.builder()
                        .timestamp(turn.getTimestamp())
                        .sender("scammer")
                        .message(turn.getUserMessage())
                        .build())
                .collect(Collectors.toList());

        ScamReport report = ScamReport.builder()
                .reportId("RPT-" + System.currentTimeMillis())
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .scamType(evidence.getScamType())
                .threatLevel(evidence.getThreatLevel())
                .totalMessages(evidence.getConversation().size())
                .upiIds(info.getUpiIds())
                .bankAccounts(info.getBankAccountNumbers())
                .ifscCodes(info.getIfscCodes())
                .phoneNumbers(info.getPhoneNumbers())
                .urls(info.getUrls())
                .suspiciousKeywords(info.getSuspiciousKeywords())
                .conversation(conversation)
                .victimProfile("Elderly Indian persona (Varutri AI)")
                .reportedBy("Varutri Honeypot System")
                .status(ScamReport.ReportStatus.PENDING)
                .build();

        log.info("📋 Generated report {} for session {}", report.getReportId(), sessionId);
        return report;
    }

    /**
     * Send report to government authorities
     */
    public void sendToAuthorities(ScamReport report) {
        try {
            // Generate report text
            String reportText = formatReportForEmail(report);

            // In production, integrate with email service (JavaMail, SendGrid, etc.)
            // For now, log and save to file
            log.warn("📧 GOVERNMENT REPORT - Would send to {}", reportEmail);
            log.warn("Report ID: {}", report.getReportId());
            log.warn("Threat Level: {}", String.format("%.2f", report.getThreatLevel()));
            log.warn("Intelligence: {} UPI IDs, {} Bank Accounts, {} Phone Numbers, {} URLs",
                    report.getUpiIds().size(),
                    report.getBankAccounts().size(),
                    report.getPhoneNumbers().size(),
                    report.getUrls().size());

            // Save to file for manual submission
            saveReportToFile(report, reportText);

            report.setStatus(ScamReport.ReportStatus.SENT);
            archiveReport(report);

            log.info("✅ Report {} sent successfully", report.getReportId());

        } catch (Exception e) {
            log.error("❌ Failed to send report {}: {}", report.getReportId(), e.getMessage(), e);
            report.setStatus(ScamReport.ReportStatus.FAILED);
            archiveReport(report);
        }
    }

    /**
     * Format report for email submission
     */
    private String formatReportForEmail(ScamReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("=".repeat(80)).append("\n");
        sb.append("CYBERCRIME REPORT - VARUTRI HONEYPOT SYSTEM\n");
        sb.append("=".repeat(80)).append("\n\n");

        sb.append("Report ID: ").append(report.getReportId()).append("\n");
        sb.append("Date: ").append(report.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("Session ID: ").append(report.getSessionId()).append("\n");
        sb.append("Scam Type: ").append(report.getScamType()).append("\n");
        sb.append("Threat Level: ").append(String.format("%.2f", report.getThreatLevel())).append("/1.0\n");
        sb.append("Total Messages: ").append(report.getTotalMessages()).append("\n\n");

        sb.append("-".repeat(80)).append("\n");
        sb.append("EXTRACTED INTELLIGENCE\n");
        sb.append("-".repeat(80)).append("\n\n");

        if (!report.getUpiIds().isEmpty()) {
            sb.append("UPI IDs:\n");
            report.getUpiIds().forEach(upi -> sb.append("  - ").append(upi).append("\n"));
            sb.append("\n");
        }

        if (!report.getBankAccounts().isEmpty()) {
            sb.append("Bank Account Numbers:\n");
            report.getBankAccounts().forEach(acc -> sb.append("  - ").append(acc).append("\n"));
            sb.append("\n");
        }

        if (!report.getIfscCodes().isEmpty()) {
            sb.append("IFSC Codes:\n");
            report.getIfscCodes().forEach(ifsc -> sb.append("  - ").append(ifsc).append("\n"));
            sb.append("\n");
        }

        if (!report.getPhoneNumbers().isEmpty()) {
            sb.append("Phone Numbers:\n");
            report.getPhoneNumbers().forEach(phone -> sb.append("  - ").append(phone).append("\n"));
            sb.append("\n");
        }

        if (!report.getUrls().isEmpty()) {
            sb.append("Phishing URLs:\n");
            report.getUrls().forEach(url -> sb.append("  - ").append(url).append("\n"));
            sb.append("\n");
        }

        if (!report.getSuspiciousKeywords().isEmpty()) {
            sb.append("Suspicious Keywords:\n");
            sb.append("  ").append(String.join(", ", report.getSuspiciousKeywords())).append("\n\n");
        }

        sb.append("-".repeat(80)).append("\n");
        sb.append("CONVERSATION TRANSCRIPT\n");
        sb.append("-".repeat(80)).append("\n\n");

        for (int i = 0; i < report.getConversation().size(); i++) {
            ScamReport.ConversationTurn turn = report.getConversation().get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append(turn.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            sb.append(" - SCAMMER:\n");
            sb.append(turn.getMessage()).append("\n\n");
        }

        sb.append("-".repeat(80)).append("\n");
        sb.append("SYSTEM INFORMATION\n");
        sb.append("-".repeat(80)).append("\n\n");
        sb.append("Reported By: ").append(report.getReportedBy()).append("\n");
        sb.append("Victim Profile: ").append(report.getVictimProfile()).append("\n");
        sb.append("Detection Method: AI-powered honeypot conversation analysis\n\n");

        sb.append("=".repeat(80)).append("\n");
        sb.append("END OF REPORT\n");
        sb.append("=".repeat(80)).append("\n");

        return sb.toString();
    }

    /**
     * Save report to file for manual submission
     */
    private void saveReportToFile(ScamReport report, String reportText) {
        try {
            File reportsDir = new File("reports");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
            }

            String filename = String.format("reports/%s_%s.txt",
                    report.getReportId(),
                    report.getTimestamp().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            try (FileWriter writer = new FileWriter(filename)) {
                writer.write(reportText);
            }

            log.info("💾 Report saved to file: {}", filename);

        } catch (Exception e) {
            log.error("Failed to save report to file: {}", e.getMessage(), e);
        }
    }

    /**
     * Archive report for audit trail
     */
    public void archiveReport(ScamReport report) {
        report.setStatus(ScamReport.ReportStatus.ARCHIVED);
        reportArchive.put(report.getReportId(), report);
        log.info("📦 Report {} archived", report.getReportId());
    }

    /**
     * Get archived report
     */
    public ScamReport getReport(String reportId) {
        return reportArchive.get(reportId);
    }

    /**
     * Get all archived reports
     */
    public List<ScamReport> getAllReports() {
        return new ArrayList<>(reportArchive.values());
    }

    /**
     * Process automatic report for high-threat session
     */
    public void processAutoReport(String sessionId) {
        if (shouldAutoReport(sessionId)) {
            ScamReport report = generateReport(sessionId);
            if (report != null) {
                sendToAuthorities(report);
            }
        }
    }
}
