package com.varutri.honeypot.service.core;

import com.varutri.honeypot.dto.ConsumerCapabilitiesResponse;
import com.varutri.honeypot.dto.ConsumerHistoryDetailResponse;
import com.varutri.honeypot.dto.ConsumerHistoryItemResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Cache facade for consumer query responses.
 * Supports MEMORY, REDIS, and HYBRID modes for horizontal scale.
 */
@Slf4j
@Service
public class ConsumerCacheService {

    @Value("${consumer.cache.history.ttl-seconds:45}")
    private long historyTtlSeconds;

    @Value("${consumer.cache.capabilities.ttl-seconds:300}")
    private long capabilitiesTtlSeconds;

    @Value("${consumer.cache.backend:MEMORY}")
    private String cacheBackendRaw;

    @Value("${consumer.cache.key-prefix:varutri:consumer}")
    private String cacheKeyPrefix;

    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    private final AtomicLong localHistoryVersion = new AtomicLong(1);
    private volatile CacheBackend cacheBackend = CacheBackend.MEMORY;
    private volatile boolean redisFailureLogged;

    private final Map<String, CacheEntry<List<ConsumerHistoryItemResponse>>> historyListCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<ConsumerHistoryDetailResponse>> historyDetailCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<ConsumerCapabilitiesResponse>> capabilitiesCache = new ConcurrentHashMap<>();

    public ConsumerCacheService(ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.objectMapper = objectMapper;
        this.redisTemplateProvider = redisTemplateProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @PostConstruct
    void initializeBackend() {
        this.cacheBackend = CacheBackend.from(cacheBackendRaw);
        log.info("Consumer cache backend initialized: {}", this.cacheBackend);
    }

    public List<ConsumerHistoryItemResponse> getOrLoadHistoryList(int limit,
            Supplier<List<ConsumerHistoryItemResponse>> loader) {
        long version = resolveHistoryVersion();
        String key = historyListKey(version, limit);
        CacheEntry<List<ConsumerHistoryItemResponse>> entry = historyListCache.get(key);
        if (isValid(entry)) {
            recordCacheMetric("hit", "memory", "history_list");
            return entry.value();
        }

        if (cacheBackend.usesRedis()) {
            Optional<List<ConsumerHistoryItemResponse>> redisValue = readRedisValue(
                    key,
                    new TypeReference<List<ConsumerHistoryItemResponse>>() {
                    },
                    "history_list");
            if (redisValue.isPresent()) {
                List<ConsumerHistoryItemResponse> loaded = redisValue.get();
                historyListCache.put(key, new CacheEntry<>(loaded, nowMs() + historyTtlMs()));
                recordCacheMetric("hit", "redis", "history_list");
                return loaded;
            }
        }

        recordCacheMetric("miss", "origin", "history_list");
        List<ConsumerHistoryItemResponse> loaded = loader.get();
        historyListCache.put(key, new CacheEntry<>(loaded, nowMs() + historyTtlMs()));
        recordCacheMetric("write", "memory", "history_list");
        if (cacheBackend.usesRedis()) {
            writeRedisValue(key, loaded, historyTtlMs(), "history_list");
        }
        return loaded;
    }

    public ConsumerHistoryDetailResponse getOrLoadHistoryDetail(String sessionId,
            Supplier<ConsumerHistoryDetailResponse> loader) {
        long version = resolveHistoryVersion();
        String key = historyDetailKey(version, sessionId);
        CacheEntry<ConsumerHistoryDetailResponse> entry = historyDetailCache.get(key);
        if (isValid(entry)) {
            recordCacheMetric("hit", "memory", "history_detail");
            return entry.value();
        }

        if (cacheBackend.usesRedis()) {
            Optional<ConsumerHistoryDetailResponse> redisValue = readRedisValue(
                    key,
                    new TypeReference<ConsumerHistoryDetailResponse>() {
                    },
                    "history_detail");
            if (redisValue.isPresent()) {
                ConsumerHistoryDetailResponse loaded = redisValue.get();
                historyDetailCache.put(key, new CacheEntry<>(loaded, nowMs() + historyTtlMs()));
                recordCacheMetric("hit", "redis", "history_detail");
                return loaded;
            }
        }

        recordCacheMetric("miss", "origin", "history_detail");
        ConsumerHistoryDetailResponse loaded = loader.get();
        historyDetailCache.put(key, new CacheEntry<>(loaded, nowMs() + historyTtlMs()));
        recordCacheMetric("write", "memory", "history_detail");
        if (cacheBackend.usesRedis()) {
            writeRedisValue(key, loaded, historyTtlMs(), "history_detail");
        }
        return loaded;
    }

    public ConsumerCapabilitiesResponse getOrLoadCapabilities(String platform,
            Supplier<ConsumerCapabilitiesResponse> loader) {
        String key = capabilitiesKey(platform);
        CacheEntry<ConsumerCapabilitiesResponse> entry = capabilitiesCache.get(key);
        if (isValid(entry)) {
            recordCacheMetric("hit", "memory", "capabilities");
            return entry.value();
        }

        if (cacheBackend.usesRedis()) {
            Optional<ConsumerCapabilitiesResponse> redisValue = readRedisValue(
                    key,
                    new TypeReference<ConsumerCapabilitiesResponse>() {
                    },
                    "capabilities");
            if (redisValue.isPresent()) {
                ConsumerCapabilitiesResponse loaded = redisValue.get();
                capabilitiesCache.put(key, new CacheEntry<>(loaded, nowMs() + capabilitiesTtlMs()));
                recordCacheMetric("hit", "redis", "capabilities");
                return loaded;
            }
        }

        recordCacheMetric("miss", "origin", "capabilities");
        ConsumerCapabilitiesResponse loaded = loader.get();
        capabilitiesCache.put(key, new CacheEntry<>(loaded, nowMs() + capabilitiesTtlMs()));
        recordCacheMetric("write", "memory", "capabilities");
        if (cacheBackend.usesRedis()) {
            writeRedisValue(key, loaded, capabilitiesTtlMs(), "capabilities");
        }
        return loaded;
    }

    public void invalidateHistory(String sessionId) {
        localHistoryVersion.incrementAndGet();
        historyListCache.clear();
        historyDetailCache.clear();
        recordCacheMetric("invalidate", "memory", "history");

        if (cacheBackend.usesRedis()) {
            incrementRedisHistoryVersion();
        }
    }

    public void clearAll() {
        localHistoryVersion.incrementAndGet();
        historyListCache.clear();
        historyDetailCache.clear();
        capabilitiesCache.clear();
        recordCacheMetric("invalidate", "memory", "all");

        if (cacheBackend.usesRedis()) {
            incrementRedisHistoryVersion();
            clearRedisCapabilities();
        }
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

    private long resolveHistoryVersion() {
        if (!cacheBackend.usesRedis()) {
            return localHistoryVersion.get();
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return localHistoryVersion.get();
        }

        try {
            String key = historyVersionKey();
            String current = redisTemplate.opsForValue().get(key);
            if (current != null) {
                long parsed = Long.parseLong(current);
                localHistoryVersion.updateAndGet(existing -> Math.max(existing, parsed));
                redisFailureLogged = false;
                recordCacheMetric("hit", "redis", "history_version");
                return parsed;
            }

            long initialized = Math.max(1L, localHistoryVersion.get());
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initialized));
            redisFailureLogged = false;
            recordCacheMetric("write", "redis", "history_version");
            return initialized;
        } catch (Exception ex) {
            logRedisFallback("resolve history version", ex);
            return localHistoryVersion.get();
        }
    }

    private void incrementRedisHistoryVersion() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }

        try {
            redisTemplate.opsForValue().increment(historyVersionKey());
            redisFailureLogged = false;
            recordCacheMetric("invalidate", "redis", "history_version");
        } catch (Exception ex) {
            logRedisFallback("increment history version", ex);
        }
    }

    private void clearRedisCapabilities() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }

        try {
            String pattern = cacheKeyPrefix + ":capabilities:*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            redisFailureLogged = false;
            recordCacheMetric("invalidate", "redis", "capabilities");
        } catch (Exception ex) {
            logRedisFallback("clear capabilities cache", ex);
        }
    }

    private String historyVersionKey() {
        return cacheKeyPrefix + ":history:version";
    }

    private String historyListKey(long version, int limit) {
        return cacheKeyPrefix + ":history:list:v" + version + ":" + limit;
    }

    private String historyDetailKey(long version, String sessionId) {
        return cacheKeyPrefix + ":history:detail:v" + version + ":" + sessionId;
    }

    private String capabilitiesKey(String platform) {
        return cacheKeyPrefix + ":capabilities:" + platform;
    }

    private <T> Optional<T> readRedisValue(String key, TypeReference<T> typeReference, String segment) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return Optional.empty();
        }

        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                recordCacheMetric("miss", "redis", segment);
                return Optional.empty();
            }
            T parsed = objectMapper.readValue(payload, typeReference);
            redisFailureLogged = false;
            return Optional.ofNullable(parsed);
        } catch (Exception ex) {
            logRedisFallback("read cache key " + key, ex);
            return Optional.empty();
        }
    }

    private void writeRedisValue(String key, Object value, long ttlMs, String segment) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, ttlMs, TimeUnit.MILLISECONDS);
            redisFailureLogged = false;
            recordCacheMetric("write", "redis", segment);
        } catch (Exception ex) {
            logRedisFallback("write cache key " + key, ex);
        }
    }

    private void logRedisFallback(String action, Exception ex) {
        if (!redisFailureLogged) {
            redisFailureLogged = true;
            log.warn("Redis cache operation failed during '{}'. Falling back to in-memory cache: {}",
                    action,
                    ex.getMessage());
            recordCacheMetric("fallback", "redis", "operation");
        } else {
            log.debug("Redis cache operation failed during '{}': {}", action, ex.getMessage());
        }
    }

    private void recordCacheMetric(String outcome, String layer, String segment) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
                "varutri.consumer.cache.operations",
                "outcome", outcome,
                "layer", layer,
                "segment", segment,
                "backend", cacheBackend.name().toLowerCase())
                .increment();
    }

    private boolean isValid(CacheEntry<?> entry) {
        return entry != null && entry.expiresAtEpochMs() > nowMs();
    }

    private enum CacheBackend {
        MEMORY,
        REDIS,
        HYBRID;

        static CacheBackend from(String raw) {
            if (raw == null || raw.isBlank()) {
                return MEMORY;
            }

            try {
                return CacheBackend.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return MEMORY;
            }
        }

        boolean usesRedis() {
            return this == REDIS || this == HYBRID;
        }
    }

    private record CacheEntry<T>(T value, long expiresAtEpochMs) {
    }
}