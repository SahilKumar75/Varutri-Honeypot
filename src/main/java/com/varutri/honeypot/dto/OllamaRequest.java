package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request format for Ollama API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("stream")
    private Boolean stream = false;

    @JsonProperty("options")
    private OllamaOptions options;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OllamaOptions {
        @JsonProperty("temperature")
        private Double temperature = 0.7;

        @JsonProperty("top_p")
        private Double topP = 0.9;

        @JsonProperty("max_tokens")
        private Integer maxTokens = 512;
    }
}
