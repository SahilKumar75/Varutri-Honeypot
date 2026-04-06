package com.varutri.honeypot.controller;

import com.varutri.honeypot.dto.ApiResponse;
import com.varutri.honeypot.dto.ConsumerAuthTokenRequest;
import com.varutri.honeypot.dto.ConsumerAuthTokenResponse;
import com.varutri.honeypot.service.security.ConsumerTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consumer auth controller for issuing short-lived bearer tokens.
 */
@Slf4j
@RestController
@RequestMapping("/api/consumer/auth")
@RequiredArgsConstructor
public class ConsumerAuthController {

    private final ConsumerTokenService consumerTokenService;

    /**
     * Exchange app/device identity for short-lived bearer token.
     */
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<ConsumerAuthTokenResponse>> issueToken(
            @Valid @RequestBody ConsumerAuthTokenRequest request) {

        log.info("Consumer token requested for appId={}, platform={}", request.getAppId(), request.getPlatform());
        ConsumerAuthTokenResponse response = consumerTokenService.issueToken(request);

        return ApiResponse.ok(response, "Consumer token issued successfully");
    }
}