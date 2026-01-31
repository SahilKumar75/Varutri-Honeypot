package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.FinalResultRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to extract intelligence from conversations using regex patterns
 */
@Slf4j
@Service
public class IntelligenceExtractor {

    @Value("${varutri.intelligence.enable-logging:true}")
    private boolean enableLogging;

    // UPI ID Pattern: [a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}
    private static final Pattern UPI_PATTERN = Pattern.compile(
            "\\b([a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64})\\b",
            Pattern.CASE_INSENSITIVE);

    // Bank Account Pattern (10-18 digits)
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
            "\\b(\\d{10,18})\\b");

    // IFSC Code Pattern (e.g., SBIN0001234)
    private static final Pattern IFSC_PATTERN = Pattern.compile(
            "\\b([A-Z]{4}0[A-Z0-9]{6})\\b");

    // URL Pattern for phishing links
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*?(?=\\s|$)",
            Pattern.CASE_INSENSITIVE);

    // Keywords that suggest phishing/scam URLs
    private static final Set<String> SUSPICIOUS_KEYWORDS = Set.of(
            "verify", "confirm", "account", "bank", "secure", "update",
            "payment", "refund", "prize", "winner", "urgent", "click");

    /**
     * Extract UPI IDs from text
     */
    public Set<String> extractUpiIds(String text) {
        Set<String> upiIds = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return upiIds;
        }

        Matcher matcher = UPI_PATTERN.matcher(text);
        while (matcher.find()) {
            String upiId = matcher.group(1);
            upiIds.add(upiId);
            if (enableLogging) {
                log.info("🎯 UPI ID detected: {}", upiId);
            }
        }

        return upiIds;
    }

    /**
     * Extract bank account information from text
     */
    public List<FinalResultRequest.BankAccountInfo> extractBankAccounts(String text) {
        List<FinalResultRequest.BankAccountInfo> accounts = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return accounts;
        }

        Set<String> accountNumbers = new HashSet<>();
        Set<String> ifscCodes = new HashSet<>();

        // Extract account numbers
        Matcher accountMatcher = ACCOUNT_NUMBER_PATTERN.matcher(text);
        while (accountMatcher.find()) {
            accountNumbers.add(accountMatcher.group(1));
        }

        // Extract IFSC codes
        Matcher ifscMatcher = IFSC_PATTERN.matcher(text);
        while (ifscMatcher.find()) {
            ifscCodes.add(ifscMatcher.group(1));
        }

        // Match accounts with IFSC codes if possible
        for (String accountNumber : accountNumbers) {
            String ifscCode = ifscCodes.isEmpty() ? null : ifscCodes.iterator().next();
            accounts.add(FinalResultRequest.BankAccountInfo.builder()
                    .accountNumber(accountNumber)
                    .ifscCode(ifscCode)
                    .build());

            if (enableLogging) {
                log.info("🎯 Bank Account detected: {} (IFSC: {})", accountNumber, ifscCode);
            }
        }

        return accounts;
    }

    /**
     * Extract phishing URLs from text
     */
    public Set<String> extractPhishingUrls(String text) {
        Set<String> phishingUrls = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return phishingUrls;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group(0);

            // Check if URL contains suspicious keywords
            if (isSuspiciousUrl(url)) {
                phishingUrls.add(url);
                if (enableLogging) {
                    log.info("🎯 Phishing URL detected: {}", url);
                }
            }
        }

        return phishingUrls;
    }

    /**
     * Check if URL is suspicious based on keywords
     */
    private boolean isSuspiciousUrl(String url) {
        String lowerUrl = url.toLowerCase();
        return SUSPICIOUS_KEYWORDS.stream()
                .anyMatch(lowerUrl::contains);
    }

    /**
     * Extract all intelligence from conversation history
     */
    public IntelligenceData extractAllIntelligence(List<String> messages) {
        Set<String> allUpiIds = new HashSet<>();
        List<FinalResultRequest.BankAccountInfo> allBankAccounts = new ArrayList<>();
        Set<String> allPhishingUrls = new HashSet<>();

        for (String message : messages) {
            allUpiIds.addAll(extractUpiIds(message));
            allBankAccounts.addAll(extractBankAccounts(message));
            allPhishingUrls.addAll(extractPhishingUrls(message));
        }

        return new IntelligenceData(
                new ArrayList<>(allUpiIds),
                allBankAccounts,
                new ArrayList<>(allPhishingUrls));
    }

    /**
     * Container for extracted intelligence
     */
    public record IntelligenceData(
            List<String> upiIds,
            List<FinalResultRequest.BankAccountInfo> bankAccounts,
            List<String> phishingUrls) {
    }
}
