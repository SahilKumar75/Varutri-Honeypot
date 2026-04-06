package com.varutri.honeypot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for issuing short-lived consumer bearer tokens.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerAuthTokenRequest {

    @NotBlank(message = "App ID is required")
    @Size(max = 100, message = "App ID cannot exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "App ID contains invalid characters")
    private String appId;

    @NotBlank(message = "Device ID is required")
    @Size(max = 180, message = "Device ID cannot exceed 180 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._:-]+$", message = "Device ID contains invalid characters")
    private String deviceId;

    @NotBlank(message = "Platform is required")
    @Size(max = 30, message = "Platform cannot exceed 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Platform contains invalid characters")
    private String platform;

    @Size(max = 40, message = "App version cannot exceed 40 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]*$", message = "App version contains invalid characters")
    private String appVersion;
}