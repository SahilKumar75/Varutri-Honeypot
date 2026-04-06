package com.varutri.honeypot.service.security;

import com.varutri.honeypot.dto.ConsumerAuthTokenRequest;
import com.varutri.honeypot.dto.ConsumerAuthTokenResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Issues and validates short-lived consumer bearer tokens.
 */
@Slf4j
@Service
public class ConsumerTokenService {

    @Value("${consumer.auth.token.secret:${VARUTRI_API_KEY:dev-consumer-secret-change-me}}")
    private String tokenSecret;

    @Value("${consumer.auth.token.ttl-minutes:240}")
    private long tokenTtlMinutes;

    @Value("${consumer.auth.allowed-app-ids:varutri-mobile,varutri-extension,varutri-web}")
    private String allowedAppIdsCsv;

    public ConsumerAuthTokenResponse issueToken(ConsumerAuthTokenRequest request) {
        String normalizedAppId = request.getAppId().trim().toLowerCase(Locale.ROOT);
        String normalizedDeviceId = request.getDeviceId().trim();
        String normalizedPlatform = request.getPlatform().trim().toUpperCase(Locale.ROOT);

        validateAppId(normalizedAppId);

        long issuedAt = Instant.now().toEpochMilli();
        long expiresAt = issuedAt + (tokenTtlMinutes * 60_000);

        String payload = String.join("|",
                normalizedAppId,
                normalizedDeviceId,
                normalizedPlatform,
                String.valueOf(issuedAt),
                String.valueOf(expiresAt));

        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload);
        String token = encodedPayload + "." + signature;

        long expiresInSeconds = Math.max((expiresAt - Instant.now().toEpochMilli()) / 1000, 0);

        return ConsumerAuthTokenResponse.builder()
                .tokenType("Bearer")
                .accessToken(token)
                .expiresAtEpochMs(expiresAt)
                .expiresInSeconds(expiresInSeconds)
                .appId(normalizedAppId)
                .deviceId(normalizedDeviceId)
                .platform(normalizedPlatform)
                .build();
    }

    public Optional<TokenClaims> validateToken(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        String encodedPayload = parts[0];
        String providedSignature = parts[1];

        String expectedSignature = sign(encodedPayload);
        if (!java.security.MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        String[] fields = payload.split("\\|", 5);
        if (fields.length != 5) {
            return Optional.empty();
        }

        String appId = fields[0];
        String deviceId = fields[1];
        String platform = fields[2];

        long issuedAt;
        long expiresAt;
        try {
            issuedAt = Long.parseLong(fields[3]);
            expiresAt = Long.parseLong(fields[4]);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        if (Instant.now().toEpochMilli() > expiresAt) {
            return Optional.empty();
        }

        if (!isAllowedAppId(appId)) {
            return Optional.empty();
        }

        return Optional.of(new TokenClaims(appId, deviceId, platform, issuedAt, expiresAt));
    }

    private void validateAppId(String appId) {
        if (!isAllowedAppId(appId)) {
            throw new IllegalArgumentException("Unsupported consumer app ID: " + appId);
        }
    }

    private boolean isAllowedAppId(String appId) {
        List<String> allowed = parseAllowedAppIds();
        return allowed.contains(appId);
    }

    private List<String> parseAllowedAppIds() {
        List<String> ids = new ArrayList<>();
        if (allowedAppIdsCsv == null || allowedAppIdsCsv.isBlank()) {
            return ids;
        }
        for (String raw : allowedAppIdsCsv.split(",")) {
            String trimmed = raw.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isBlank()) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            log.error("Failed to sign consumer token", ex);
            throw new IllegalStateException("Failed to sign consumer token");
        }
    }

    @Data
    @AllArgsConstructor
    public static class TokenClaims {
        private String appId;
        private String deviceId;
        private String platform;
        private long issuedAtEpochMs;
        private long expiresAtEpochMs;
    }
}