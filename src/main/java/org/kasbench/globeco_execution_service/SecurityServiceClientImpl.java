package org.kasbench.globeco_execution_service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of SecurityServiceClient using RestTemplate with Caffeine caching.
 */
@Component
public class SecurityServiceClientImpl implements SecurityServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(SecurityServiceClientImpl.class);
    
    private final RestTemplate restTemplate;
    private final String securityServiceBaseUrl;
    private final Cache<String, String> tickerCache;
    private final Cache<String, String> reverseTickerCache;
    
    public SecurityServiceClientImpl(
            @Qualifier("securityServiceRestTemplate") RestTemplate restTemplate,
            @Value("${security-service.base-url:http://globeco-security-service:8000}") String securityServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.securityServiceBaseUrl = securityServiceBaseUrl;
        this.tickerCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();
        this.reverseTickerCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();
    }
    
    @Override
    public Optional<SecurityDTO> getSecurityById(String securityId) {
        if (securityId == null || securityId.trim().isEmpty()) {
            logger.warn("Security ID is null or empty");
            return Optional.empty();
        }
        
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(securityServiceBaseUrl + "/api/v2/securities")
                    .queryParam("securityId", securityId)
                    .toUriString();
            
            logger.debug("Calling Security Service: {}", url);
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> securities = (List<Map<String, Object>>) responseBody.get("securities");
                
                if (securities != null && !securities.isEmpty()) {
                    Map<String, Object> security = securities.get(0);
                    String ticker = (String) security.get("ticker");
                    
                    // Cache the ticker mapping
                    if (ticker != null) {
                        tickerCache.put("security:" + securityId, ticker);
                        logger.debug("Cached ticker {} for security ID {}", ticker, securityId);
                    }
                    
                    return Optional.of(new SecurityDTO(securityId, ticker));
                }
            }
            
            logger.warn("Security not found for ID: {}", securityId);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error calling Security Service for security ID {}: {}", securityId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<String> getTickerBySecurityId(String securityId) {
        if (securityId == null || securityId.trim().isEmpty()) {
            logger.warn("Security ID is null or empty");
            return Optional.empty();
        }
        
        String cacheKey = "security:" + securityId;
        
        // Check cache first
        String cachedTicker = tickerCache.getIfPresent(cacheKey);
        if (cachedTicker != null) {
            logger.debug("Cache hit for security ID: {}", securityId);
            logCacheStats();
            return Optional.of(cachedTicker);
        }
        
        logger.debug("Cache miss for security ID: {}", securityId);
        
        // Fetch from Security Service
        Optional<SecurityDTO> securityDTO = getSecurityById(securityId);
        
        if (securityDTO.isPresent() && securityDTO.get().getTicker() != null) {
            String ticker = securityDTO.get().getTicker();
            tickerCache.put(cacheKey, ticker);
            logger.debug("Cached ticker {} for security ID {}", ticker, securityId);
            logCacheStats();
            return Optional.of(ticker);
        }
        
        logger.warn("Ticker not found for security ID: {}", securityId);
        logCacheStats();
        return Optional.empty();
    }
    
    /**
     * Log cache statistics for monitoring.
     */
    private void logCacheStats() {
        var stats = tickerCache.stats();
        logger.debug("Cache stats - Size: {}, Hit rate: {:.2f}%, Miss count: {}, Eviction count: {}", 
                tickerCache.estimatedSize(),
                stats.hitRate() * 100,
                stats.missCount(),
                stats.evictionCount());
    }
    
    /**
     * Get cache statistics for monitoring endpoints.
     * @return Cache statistics for both caches
     */
    public Map<String, Object> getCacheStats() {
        var tickerStats = tickerCache.stats();
        var reverseTickerStats = reverseTickerCache.stats();
        
        return Map.of(
                "tickerCache", Map.of(
                        "size", tickerCache.estimatedSize(),
                        "hitRate", tickerStats.hitRate(),
                        "hitCount", tickerStats.hitCount(),
                        "missCount", tickerStats.missCount(),
                        "evictionCount", tickerStats.evictionCount(),
                        "averageLoadPenalty", tickerStats.averageLoadPenalty()
                ),
                "reverseTickerCache", Map.of(
                        "size", reverseTickerCache.estimatedSize(),
                        "hitRate", reverseTickerStats.hitRate(),
                        "hitCount", reverseTickerStats.hitCount(),
                        "missCount", reverseTickerStats.missCount(),
                        "evictionCount", reverseTickerStats.evictionCount(),
                        "averageLoadPenalty", reverseTickerStats.averageLoadPenalty()
                ),
                "totalSize", tickerCache.estimatedSize() + reverseTickerCache.estimatedSize(),
                "combinedHitRate", (tickerStats.hitCount() + reverseTickerStats.hitCount()) / 
                                  (double)(tickerStats.requestCount() + reverseTickerStats.requestCount())
        );
    }
    
    /**
     * Clear the cache (for testing purposes).
     */
    public void clearCache() {
        tickerCache.invalidateAll();
        reverseTickerCache.invalidateAll();
        logger.info("Security service cache cleared");
    }
    
    @Override
    public Optional<String> getSecurityIdByTicker(String ticker) {
        if (ticker == null || ticker.trim().isEmpty()) {
            logger.warn("Ticker is null or empty");
            return Optional.empty();
        }
        
        String cacheKey = "ticker:" + ticker.trim().toUpperCase();
        
        // Check cache first
        String cachedSecurityId = reverseTickerCache.getIfPresent(cacheKey);
        if (cachedSecurityId != null) {
            logger.debug("Cache hit for ticker: {}", ticker);
            logReverseCacheStats();
            return Optional.of(cachedSecurityId);
        }
        
        logger.debug("Cache miss for ticker: {}", ticker);
        
        // Fetch from Security Service using ticker parameter
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(securityServiceBaseUrl + "/api/v2/securities")
                    .queryParam("ticker", ticker.trim())
                    .toUriString();
            
            logger.debug("Calling Security Service for ticker: {}", url);
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> securities = (List<Map<String, Object>>) responseBody.get("securities");
                
                if (securities != null && !securities.isEmpty()) {
                    Map<String, Object> security = securities.get(0);
                    String securityId = (String) security.get("securityId");
                    String responseTicker = (String) security.get("ticker");
                    
                    // Cache both directions if we have valid data
                    if (securityId != null && responseTicker != null) {
                        reverseTickerCache.put(cacheKey, securityId);
                        tickerCache.put("security:" + securityId, responseTicker);
                        logger.debug("Cached security ID {} for ticker {}", securityId, ticker);
                    }
                    
                    logReverseCacheStats();
                    return Optional.of(securityId);
                }
            }
            
            logger.warn("Security not found for ticker: {}", ticker);
            logReverseCacheStats();
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error calling Security Service for ticker {}: {}", ticker, e.getMessage(), e);
            logReverseCacheStats();
            return Optional.empty();
        }
    }
    
    /**
     * Log reverse cache statistics for monitoring.
     */
    private void logReverseCacheStats() {
        var stats = reverseTickerCache.stats();
        logger.debug("Reverse cache stats - Size: {}, Hit rate: {:.2f}%, Miss count: {}, Eviction count: {}", 
                reverseTickerCache.estimatedSize(),
                stats.hitRate() * 100,
                stats.missCount(),
                stats.evictionCount());
    }
} 