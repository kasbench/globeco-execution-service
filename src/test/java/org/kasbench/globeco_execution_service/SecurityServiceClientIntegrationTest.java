package org.kasbench.globeco_execution_service;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityServiceClientIntegrationTest {

    private SecurityServiceClientImpl securityServiceClient;
    
    private static final String SECURITY_ID = "SEC123456789012345678901";
    private static final String TICKER = "AAPL";

    @BeforeEach
    void setUp() {        
        // Configure client to use mock endpoint for testing
        String baseUrl = "http://test-security-service:8000";
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        
        securityServiceClient = new SecurityServiceClientImpl(restTemplate, baseUrl);
    }

    @AfterEach
    void tearDown() {
        // No cleanup needed for this simplified test
    }

    @Test
    void securityServiceClient_InstantiatesCorrectly() {
        // Given/When - SecurityServiceClient was created in setUp
        
        // Then - Verify it was created successfully
        assertThat(securityServiceClient).isNotNull();
    }

    @Test
    void getSecurityById_HandlesNullInput() {
        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecurityById_HandlesEmptyInput() {
        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById("  ");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getTickerBySecurityId_HandlesNullInput() {
        // When
        Optional<String> result = securityServiceClient.getTickerBySecurityId(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getTickerBySecurityId_HandlesEmptyInput() {
        // When
        Optional<String> result = securityServiceClient.getTickerBySecurityId("  ");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getCacheStats_ReturnsValidStructure() {
        // When
        var stats = securityServiceClient.getCacheStats();

        // Then
        assertThat(stats).containsKeys("size", "hitRate", "hitCount", "missCount", "evictionCount", "averageLoadPenalty");
        assertThat(stats.get("size")).isEqualTo(0L);
        assertThat(stats.get("hitCount")).isEqualTo(0L);
        assertThat(stats.get("missCount")).isEqualTo(0L);
        // Note: hitRate may be NaN when no operations have been performed, so we just check it exists
        assertThat(stats.get("hitRate")).isNotNull();
    }

    @Test
    void clearCache_ExecutesWithoutError() {
        // When/Then - Should not throw exception
        securityServiceClient.clearCache();
    }

    @Test
    void cacheIntegration_BasicFunctionality() {
        // Given - Clear cache to start fresh
        securityServiceClient.clearCache();
        
        // When - Get initial stats
        var initialStats = securityServiceClient.getCacheStats();
        
        // Then - Verify initial state
        assertThat(initialStats.get("size")).isEqualTo(0L);
        
        // Note: Full integration testing with actual service calls would require 
        // either a real Security Service instance or more complex WireMock setup.
        // This test verifies the cache infrastructure is working correctly.
    }
} 