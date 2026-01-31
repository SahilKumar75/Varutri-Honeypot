package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.FinalResultRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service to send final intelligence report to GUVI Hackathon API
 */
@Slf4j
@Service
public class CallbackService {

    private final WebClient webClient;
    private final String teamId;

    public CallbackService(
            @Value("${hackathon.callback-url}") String callbackUrl,
            @Value("${hackathon.team-id}") String teamId) {
        this.teamId = teamId;
        this.webClient = WebClient.builder()
                .baseUrl(callbackUrl)
                .build();

        log.info("Callback service initialized for team: {} to URL: {}", teamId, callbackUrl);
    }

    /**
     * Send final intelligence report to hackathon API
     */
    public void sendFinalReport(String sessionId,
            List<String> upiIds,
            List<FinalResultRequest.BankAccountInfo> bankAccounts,
            List<String> phishingLinks,
            int conversationTurns) {
        try {
            FinalResultRequest request = FinalResultRequest.builder()
                    .sessionId(sessionId)
                    .teamId(teamId)
                    .upiIds(upiIds)
                    .bankAccounts(bankAccounts)
                    .phishingLinks(phishingLinks)
                    .conversationTurns(conversationTurns)
                    .timestamp(getCurrentTimestamp())
                    .build();

            log.info("📊 Sending final report for session {}: {} UPIs, {} accounts, {} URLs, {} turns",
                    sessionId, upiIds.size(), bankAccounts.size(), phishingLinks.size(), conversationTurns);

            Mono<String> responseMono = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class);

            String response = responseMono.block();

            log.info("✅ Final report sent successfully for session {}: {}", sessionId, response);

        } catch (Exception e) {
            log.error("❌ Error sending final report for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Get current timestamp in ISO format
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
