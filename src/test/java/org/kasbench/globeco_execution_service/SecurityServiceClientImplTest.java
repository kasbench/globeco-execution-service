package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceClientImplTest {

    @Mock
    private RestTemplate restTemplate;

    private SecurityServiceClientImpl securityServiceClient;

    private static final String BASE_URL = "http://test-security-service:8000";
    private static final String SECURITY_ID = "SEC123456789012345678901";
    private static final String TICKER = "AAPL";

    @BeforeEach
    void setUp() {
        securityServiceClient = new SecurityServiceClientImpl(restTemplate, BASE_URL);
    }

    @Test
    void getSecurityById_Success() {
        // Given
        Map<String, Object> securityData = Map.of(
                "securityId", SECURITY_ID,
                "ticker", TICKER,
                "description", "Apple Inc. Common Stock"
        );
        
        Map<String, Object> responseBody = Map.of(
                "securities", List.of(securityData),
                "pagination", Map.of("totalElements", 1)
        );
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(SECURITY_ID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getSecurityId()).isEqualTo(SECURITY_ID);
        assertThat(result.get().getTicker()).isEqualTo(TICKER);
        
        verify(restTemplate).getForEntity(
                contains("securityId=" + SECURITY_ID), 
                eq(Map.class)
        );
    }

    @Test
    void getSecurityById_NotFound() {
        // Given
        Map<String, Object> responseBody = Map.of(
                "securities", List.of(),
                "pagination", Map.of("totalElements", 0)
        );
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(SECURITY_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecurityById_ServiceError() {
        // Given
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(SECURITY_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecurityById_NullSecurityId() {
        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(null);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getSecurityById_EmptySecurityId() {
        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById("  ");

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTickerBySecurityId_Success() {
        // Given
        Map<String, Object> securityData = Map.of(
                "securityId", SECURITY_ID,
                "ticker", TICKER,
                "description", "Apple Inc. Common Stock"
        );
        
        Map<String, Object> responseBody = Map.of(
                "securities", List.of(securityData),
                "pagination", Map.of("totalElements", 1)
        );
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Optional<String> result = securityServiceClient.getTickerBySecurityId(SECURITY_ID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TICKER);
    }

    @Test
    void getTickerBySecurityId_CacheHit() {
        // Given - First call to populate cache
        Map<String, Object> securityData = Map.of(
                "securityId", SECURITY_ID,
                "ticker", TICKER,
                "description", "Apple Inc. Common Stock"
        );
        
        Map<String, Object> responseBody = Map.of(
                "securities", List.of(securityData),
                "pagination", Map.of("totalElements", 1)
        );
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When - First call
        Optional<String> firstResult = securityServiceClient.getTickerBySecurityId(SECURITY_ID);
        
        // When - Second call (should hit cache)
        Optional<String> secondResult = securityServiceClient.getTickerBySecurityId(SECURITY_ID);

        // Then
        assertThat(firstResult).isPresent();
        assertThat(firstResult.get()).isEqualTo(TICKER);
        assertThat(secondResult).isPresent();
        assertThat(secondResult.get()).isEqualTo(TICKER);
        
        // Verify REST call was made only once (cache hit on second call)
        verify(restTemplate, times(1)).getForEntity(any(String.class), eq(Map.class));
    }

    @Test
    void getTickerBySecurityId_NotFound() {
        // Given
        Map<String, Object> responseBody = Map.of(
                "securities", List.of(),
                "pagination", Map.of("totalElements", 0)
        );
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Optional<String> result = securityServiceClient.getTickerBySecurityId(SECURITY_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getTickerBySecurityId_NullSecurityId() {
        // When
        Optional<String> result = securityServiceClient.getTickerBySecurityId(null);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getCacheStats_ReturnsValidStats() {
        // When
        Map<String, Object> stats = securityServiceClient.getCacheStats();

        // Then
        assertThat(stats).containsKeys("size", "hitRate", "hitCount", "missCount", "evictionCount", "averageLoadPenalty");
        assertThat(stats.get("size")).isEqualTo(0L);
    }

    @Test
    void clearCache_ClearsSuccessfully() {
        // Given - Populate cache first
        Map<String, Object> securityData = Map.of(
                "securityId", SECURITY_ID,
                "ticker", TICKER,
                "description", "Apple Inc. Common Stock"
        );
        
        Map<String, Object> responseBody = Map.of(
                "securities", List.of(securityData),
                "pagination", Map.of("totalElements", 1)
        );
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // Populate cache
        securityServiceClient.getTickerBySecurityId(SECURITY_ID);

        // When
        securityServiceClient.clearCache();

        // Then - Next call should hit the service again (cache was cleared)
        securityServiceClient.getTickerBySecurityId(SECURITY_ID);
        
        verify(restTemplate, times(2)).getForEntity(any(String.class), eq(Map.class));
    }

    @Test
    void getSecurityById_HandlesNullResponseBody() {
        // Given
        ResponseEntity<Map> response = new ResponseEntity<>(null, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(SECURITY_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecurityById_HandlesNullSecuritiesList() {
        // Given
        Map<String, Object> responseBody = new java.util.HashMap<>();
        responseBody.put("securities", null);
        responseBody.put("pagination", Map.of("totalElements", 0));
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(SECURITY_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getSecurityById_HandlesNullTicker() {
        // Given
        Map<String, Object> securityData = Map.of(
                "securityId", SECURITY_ID,
                "description", "Apple Inc. Common Stock"
                // ticker is missing/null
        );
        
        Map<String, Object> responseBody = Map.of(
                "securities", List.of(securityData),
                "pagination", Map.of("totalElements", 1)
        );
        
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(any(String.class), eq(Map.class)))
                .thenReturn(response);

        // When
        Optional<SecurityDTO> result = securityServiceClient.getSecurityById(SECURITY_ID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getSecurityId()).isEqualTo(SECURITY_ID);
        assertThat(result.get().getTicker()).isNull();
    }
} 