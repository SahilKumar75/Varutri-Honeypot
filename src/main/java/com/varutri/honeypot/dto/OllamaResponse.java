package com.varutri.honeypot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response format from Ollama API
 */
@Data
@NoArgsConstructor
public class OllamaResponse {

    @JsonProperty("model")
    private String model;

    @JsonProperty("response")
    private String response;

    @JsonProperty("done")
    private Boolean done;

    @JsonProperty("context")
    private Object context;
}
