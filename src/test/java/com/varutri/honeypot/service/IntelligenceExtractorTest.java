package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.FinalResultRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

/**
 * Unit tests for IntelligenceExtractor
 */
class IntelligenceExtractorTest {

    private final IntelligenceExtractor extractor = new IntelligenceExtractor();

    @Test
    void testExtractUpiIds() {
        String text = "Please send payment to myaccount@paytm or 9876543210@ybl";
        Set<String> upiIds = extractor.extractUpiIds(text);

        assertEquals(2, upiIds.size());
        assertTrue(upiIds.contains("myaccount@paytm"));
        assertTrue(upiIds.contains("9876543210@ybl"));
    }

    @Test
    void testExtractBankAccounts() {
        String text = "My account number is 1234567890123 with IFSC SBIN0001234";
        List<FinalResultRequest.BankAccountInfo> accounts = extractor.extractBankAccounts(text);

        assertFalse(accounts.isEmpty());
        assertEquals("1234567890123", accounts.get(0).getAccountNumber());
        assertEquals("SBIN0001234", accounts.get(0).getIfscCode());
    }

    @Test
    void testExtractPhishingUrls() {
        String text = "Click here to verify your account: https://secure-bank-verify.com/account";
        Set<String> urls = extractor.extractPhishingUrls(text);

        assertFalse(urls.isEmpty());
        assertTrue(urls.stream().anyMatch(url -> url.contains("secure-bank-verify")));
    }

    @Test
    void testNoFalsePositives() {
        String text = "Hello, how are you today? I'm doing fine, thank you.";
        Set<String> upiIds = extractor.extractUpiIds(text);

        assertTrue(upiIds.isEmpty());
    }

    @Test
    void testExtractAllIntelligence() {
        List<String> messages = List.of(
                "Send money to test@paytm",
                "Account: 9876543210987, IFSC: HDFC0001234",
                "Visit https://urgent-payment-confirm.com now");

        IntelligenceExtractor.IntelligenceData data = extractor.extractAllIntelligence(messages);

        assertFalse(data.upiIds().isEmpty());
        assertFalse(data.bankAccounts().isEmpty());
        assertFalse(data.phishingUrls().isEmpty());
    }
}
