package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceClientImplTest {

    @Mock
    private RestTemplate restTemplate;

    private TradeServiceClientImpl tradeServiceClient;

    @BeforeEach
    void setUp() {
        tradeServiceClient = new TradeServiceClientImpl(
                restTemplate,
                "http://test-trade-service:8082",
                true, // retry enabled
                2     // max attempts
        );
    }

    @Test
    void testGetExecutionVersion_Success() {
        // Given
        Integer executionId = 123;
        TradeServiceExecutionResponseDTO responseDTO = new TradeServiceExecutionResponseDTO(executionId, 5);
        ResponseEntity<TradeServiceExecutionResponseDTO> response = new ResponseEntity<>(responseDTO, HttpStatus.OK);
        
        when(restTemplate.getForEntity(
                eq("http://test-trade-service:8082/api/v1/executions/123"),
                eq(TradeServiceExecutionResponseDTO.class)))
                .thenReturn(response);

        // When
        Optional<Integer> result = tradeServiceClient.getExecutionVersion(executionId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(5, result.get());
    }

    @Test
    void testGetExecutionVersion_NotFound() {
        // Given
        Integer executionId = 123;
        when(restTemplate.getForEntity(anyString(), eq(TradeServiceExecutionResponseDTO.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When
        Optional<Integer> result = tradeServiceClient.getExecutionVersion(executionId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testGetExecutionVersion_NetworkError() {
        // Given
        Integer executionId = 123;
        when(restTemplate.getForEntity(anyString(), eq(TradeServiceExecutionResponseDTO.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

        // When
        Optional<Integer> result = tradeServiceClient.getExecutionVersion(executionId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testGetExecutionVersion_NullResponse() {
        // Given
        Integer executionId = 123;
        ResponseEntity<TradeServiceExecutionResponseDTO> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(TradeServiceExecutionResponseDTO.class)))
                .thenReturn(response);

        // When
        Optional<Integer> result = tradeServiceClient.getExecutionVersion(executionId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testUpdateExecutionFill_Success() {
        // Given
        Integer executionId = 123;
        TradeServiceExecutionFillDTO fillDTO = new TradeServiceExecutionFillDTO("PART", new BigDecimal("100.00"), 5);
        ResponseEntity<String> response = new ResponseEntity<>("Success", HttpStatus.OK);
        
        when(restTemplate.exchange(
                eq("http://test-trade-service:8082/api/v1/executions/123/fill"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(response);

        // When
        boolean result = tradeServiceClient.updateExecutionFill(executionId, fillDTO);

        // Then
        assertTrue(result);
    }

    @Test
    void testUpdateExecutionFill_NotFound() {
        // Given
        Integer executionId = 123;
        TradeServiceExecutionFillDTO fillDTO = new TradeServiceExecutionFillDTO("PART", new BigDecimal("100.00"), 5);
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When
        boolean result = tradeServiceClient.updateExecutionFill(executionId, fillDTO);

        // Then
        assertFalse(result);
    }

    @Test
    void testUpdateExecutionFill_ConflictWithRetry() {
        // Given
        Integer executionId = 123;
        TradeServiceExecutionFillDTO fillDTO = new TradeServiceExecutionFillDTO("PART", new BigDecimal("100.00"), 5);
        
        // Mock conflict on first attempt, success on second
        when(restTemplate.exchange(
                eq("http://test-trade-service:8082/api/v1/executions/123/fill"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT))
                .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));
        
        // Mock successful version retrieval for retry
        TradeServiceExecutionResponseDTO responseDTO = new TradeServiceExecutionResponseDTO(executionId, 6);
        ResponseEntity<TradeServiceExecutionResponseDTO> versionResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://test-trade-service:8082/api/v1/executions/123"),
                eq(TradeServiceExecutionResponseDTO.class)))
                .thenReturn(versionResponse);

        // When
        boolean result = tradeServiceClient.updateExecutionFill(executionId, fillDTO);

        // Then
        assertTrue(result);
        assertEquals(6, fillDTO.getVersion()); // Version should be updated for retry
        verify(restTemplate, times(2)).exchange(
                eq("http://test-trade-service:8082/api/v1/executions/123/fill"),
                eq(HttpMethod.PUT), 
                any(HttpEntity.class), 
                eq(String.class));
        verify(restTemplate, times(1)).getForEntity(
                eq("http://test-trade-service:8082/api/v1/executions/123"),
                eq(TradeServiceExecutionResponseDTO.class));
    }

    @Test
    void testUpdateExecutionFill_ConflictMaxRetriesReached() {
        // Given
        Integer executionId = 123;
        TradeServiceExecutionFillDTO fillDTO = new TradeServiceExecutionFillDTO("PART", new BigDecimal("100.00"), 5);
        
        // Mock conflict on all attempts
        when(restTemplate.exchange(
                eq("http://test-trade-service:8082/api/v1/executions/123/fill"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));
        
        // Mock successful version retrieval for retry
        TradeServiceExecutionResponseDTO responseDTO = new TradeServiceExecutionResponseDTO(executionId, 6);
        ResponseEntity<TradeServiceExecutionResponseDTO> versionResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
        when(restTemplate.getForEntity(
                eq("http://test-trade-service:8082/api/v1/executions/123"),
                eq(TradeServiceExecutionResponseDTO.class)))
                .thenReturn(versionResponse);

        // When
        boolean result = tradeServiceClient.updateExecutionFill(executionId, fillDTO);

        // Then
        assertFalse(result);
        verify(restTemplate, times(2)).exchange(
                eq("http://test-trade-service:8082/api/v1/executions/123/fill"),
                eq(HttpMethod.PUT), 
                any(HttpEntity.class), 
                eq(String.class));
        verify(restTemplate, times(1)).getForEntity(
                eq("http://test-trade-service:8082/api/v1/executions/123"),
                eq(TradeServiceExecutionResponseDTO.class));
    }

    @Test
    void testUpdateExecutionFill_NetworkError() {
        // Given
        Integer executionId = 123;
        TradeServiceExecutionFillDTO fillDTO = new TradeServiceExecutionFillDTO("PART", new BigDecimal("100.00"), 5);
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timeout"));

        // When
        boolean result = tradeServiceClient.updateExecutionFill(executionId, fillDTO);

        // Then
        assertFalse(result);
    }

    @Test
    void testUpdateExecutionFill_RetryDisabled() {
        // Given - client with retry disabled
        TradeServiceClientImpl clientWithoutRetry = new TradeServiceClientImpl(
                restTemplate,
                "http://test-trade-service:8082",
                false, // retry disabled
                2
        );
        
        Integer executionId = 123;
        TradeServiceExecutionFillDTO fillDTO = new TradeServiceExecutionFillDTO("PART", new BigDecimal("100.00"), 5);
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));

        // When
        boolean result = clientWithoutRetry.updateExecutionFill(executionId, fillDTO);

        // Then
        assertFalse(result);
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
    }
} 