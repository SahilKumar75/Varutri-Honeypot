package com.varutri.honeypot.service.core;

import com.varutri.honeypot.dto.ConsumerCapabilitiesResponse;
import com.varutri.honeypot.dto.ConsumerHistoryDetailResponse;
import com.varutri.honeypot.dto.ConsumerHistoryItemResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lightweight in-memory cache for consumer query responses.
 */
@Service
public class ConsumerCacheService {

    @Value("${consumer.cache.history.ttl-seconds:45}")
    private long historyTtlSeconds;

    @Value("${consumer.cache.capabilities.ttl-seconds:300}")
    private long capabilitiesTtlSeconds;

    private final Map<String, CacheEntry<List<ConsumerHistoryItemResponse>>> historyListCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<ConsumerHistoryDetailResponse>> historyDetailCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<ConsumerCapabilitiesResponse>> capabilitiesCache = new ConcurrentHashMap<>();

    public List<ConsumerHistoryItemResponse> getOrLoadHistoryList(int limit,
            Supplier<List<ConsumerHistoryItemResponse>> loader) {
        String key = "history:list:" + limit;
        CacheEntry<List<ConsumerHistoryItemResponse>> entry = historyListCache.get(key);
        if (isValid(entry)) {
            return entry.value();
        }

        List<ConsumerHistoryItemResponse> loaded = loader.get();
        historyListCache.put(key, new CacheEntry<>(loaded, nowMs() + historyTtlMs()));
        return loaded;
    }

    public ConsumerHistoryDetailResponse getOrLoadHistoryDetail(String sessionId,
            Supplier<ConsumerHistoryDetailResponse> loader) {
        String key = "history:detail:" + sessionId;
        CacheEntry<ConsumerHistoryDetailResponse> entry = historyDetailCache.get(key);
        if (isValid(entry)) {
            return entry.value();
        }

        ConsumerHistoryDetailResponse loaded = loader.get();
        historyDetailCache.put(key, new CacheEntry<>(loaded, nowMs() + historyTtlMs()));
        return loaded;
    }

    public ConsumerCapabilitiesResponse getOrLoadCapabilities(String platform,
            Supplier<ConsumerCapabilitiesResponse> loader) {
        String key = "capabilities:" + platform;
        CacheEntry<ConsumerCapabilitiesResponse> entry = capabilitiesCache.get(key);
        if (isValid(entry)) {
            return entry.value();
        }

        ConsumerCapabilitiesResponse loaded = loader.get();
        capabilitiesCache.put(key, new CacheEntry<>(loaded, nowMs() + capabilitiesTtlMs()));
        return loaded;
    }

    public void invalidateHistory(String sessionId) {
        historyListCache.clear();
        if (sessionId != null && !sessionId.isBlank()) {
            historyDetailCache.remove("history:detail:" + sessionId);
        }
    }

    public void clearAll() {
        historyListCache.clear();
        historyDetailCache.clear();
        capabilitiesCache.clear();
    }

    private long historyTtlMs() {
        return Math.max(5_000L, historyTtlSeconds * 1_000L);
    }

    private long capabilitiesTtlMs() {
        return Math.max(30_000L, capabilitiesTtlSeconds * 1_000L);
    }

    private long nowMs() {
        return System.currentTimeMillis();
    }

    private boolean isValid(CacheEntry<?> entry) {
        return entry != null && entry.expiresAtEpochMs() > nowMs();
    }

    private record CacheEntry<T>(T value, long expiresAtEpochMs) {
    }
}