package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Final result request to send to GUVI Hackathon API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalResultRequest {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("teamId")
    private String teamId;

    @JsonProperty("upiIds")
    private List<String> upiIds;

    @JsonProperty("bankAccounts")
    private List<BankAccountInfo> bankAccounts;

    @JsonProperty("phishingLinks")
    private List<String> phishingLinks;

    @JsonProperty("conversationTurns")
    private Integer conversationTurns;

    @JsonProperty("timestamp")
    private String timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankAccountInfo {
        @JsonProperty("accountNumber")
        private String accountNumber;

        @JsonProperty("ifscCode")
        private String ifscCode;
    }
}
