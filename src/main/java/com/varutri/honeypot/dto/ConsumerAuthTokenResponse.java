package com.varutri.honeypot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for consumer bearer token issuance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerAuthTokenResponse {
    private String tokenType;
    private String accessToken;
    private long expiresAtEpochMs;
    private long expiresInSeconds;
    private String appId;
    private String deviceId;
    private String platform;
}