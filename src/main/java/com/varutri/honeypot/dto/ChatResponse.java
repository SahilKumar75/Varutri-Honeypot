package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outgoing response to the scammer/client
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    @JsonProperty("status")
    private String status; // "success" or "error"

    @JsonProperty("reply")
    private String reply;

    /**
     * Create a successful response
     */
    public static ChatResponse success(String reply) {
        return new ChatResponse("success", reply);
    }

    /**
     * Create an error response
     */
    public static ChatResponse error(String message) {
        return new ChatResponse("error", message);
    }
}
