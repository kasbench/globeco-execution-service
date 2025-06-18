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
     * @return Cache statistics
     */
    public Map<String, Object> getCacheStats() {
        var stats = tickerCache.stats();
        return Map.of(
                "size", tickerCache.estimatedSize(),
                "hitRate", stats.hitRate(),
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "evictionCount", stats.evictionCount(),
                "averageLoadPenalty", stats.averageLoadPenalty()
        );
    }
    
    /**
     * Clear the cache (for testing purposes).
     */
    public void clearCache() {
        tickerCache.invalidateAll();
        logger.info("Security service cache cleared");
    }
} 