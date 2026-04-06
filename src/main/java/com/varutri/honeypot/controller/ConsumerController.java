package com.varutri.honeypot.controller;

import com.varutri.honeypot.dto.ApiResponse;
import com.varutri.honeypot.dto.ConsumerAnalysisResponse;
import com.varutri.honeypot.dto.ConsumerHistoryDetailResponse;
import com.varutri.honeypot.dto.ConsumerHistoryItemResponse;
import com.varutri.honeypot.dto.ConsumerSignalRequest;
import com.varutri.honeypot.service.core.ConsumerHistoryService;
import com.varutri.honeypot.service.core.ConsumerSignalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consumer-facing ingestion controller.
 * Supports channel-aware suspicious content analysis for mobile and browser clients.
 */
@Slf4j
@RestController
@RequestMapping("/api/consumer")
@RequiredArgsConstructor
public class ConsumerController {

    private final ConsumerSignalService consumerSignalService;
    private final ConsumerHistoryService consumerHistoryService;

    /**
     * Analyze a suspicious signal reported by a consumer app/extension.
     * POST /api/consumer/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<ConsumerAnalysisResponse>> analyze(
            @Valid @RequestBody ConsumerSignalRequest request) {

        log.info("Consumer analysis request received for channel: {}", request.getChannel());
        ConsumerAnalysisResponse response = consumerSignalService.analyzeSignal(request);

        return ApiResponse.ok(response, "Consumer signal analyzed successfully");
    }

    /**
     * List recent consumer analysis sessions.
     * GET /api/consumer/history?limit=20
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ConsumerHistoryItemResponse>>> listHistory(
            @RequestParam(defaultValue = "20") int limit) {

        List<ConsumerHistoryItemResponse> historyItems = consumerHistoryService.getRecentHistory(limit);
        return ApiResponse.ok(historyItems, "Consumer history retrieved successfully");
    }

    /**
     * Get detail of one consumer analysis session.
     * GET /api/consumer/history/{sessionId}
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<ApiResponse<ConsumerHistoryDetailResponse>> getHistoryDetail(
            @PathVariable String sessionId) {

        ConsumerHistoryDetailResponse detail = consumerHistoryService.getHistoryDetail(sessionId);
        return ApiResponse.ok(detail, "Consumer history detail retrieved successfully");
    }
}