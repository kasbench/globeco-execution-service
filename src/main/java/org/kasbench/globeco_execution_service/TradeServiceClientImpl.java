package org.kasbench.globeco_execution_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Implementation of TradeServiceClient for communicating with the trade service.
 */
@Component
public class TradeServiceClientImpl implements TradeServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(TradeServiceClientImpl.class);
    
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean retryEnabled;
    private final int maxRetryAttempts;

    public TradeServiceClientImpl(
            @Qualifier("tradeServiceRestTemplate") RestTemplate restTemplate,
            @Value("${trade.service.base-url:http://globeco-trade-service:8082}") String baseUrl,
            @Value("${trade.service.retry.enabled:true}") boolean retryEnabled,
            @Value("${trade.service.retry.max-attempts:2}") int maxRetryAttempts) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.retryEnabled = retryEnabled;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    @Override
    public Optional<Integer> getExecutionVersion(Integer executionId) {
        String url = baseUrl + "/api/v1/executions/" + executionId;
        
        try {
            logger.debug("Getting execution version from trade service: {}", url);
            ResponseEntity<TradeServiceExecutionResponseDTO> response = restTemplate.getForEntity(
                    url, TradeServiceExecutionResponseDTO.class);
            
            if (response.getBody() != null && response.getBody().getVersion() != null) {
                logger.debug("Retrieved version {} for execution {}", response.getBody().getVersion(), executionId);
                return Optional.of(response.getBody().getVersion());
            } else {
                logger.warn("No version found in response for execution {}", executionId);
                return Optional.empty();
            }
        } catch (HttpClientErrorException.NotFound e) {
            logger.warn("Execution {} not found in trade service", executionId);
            return Optional.empty();
        } catch (ResourceAccessException e) {
            logger.error("Network error accessing trade service for execution {}: {}", executionId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error getting execution version for {}: {}", executionId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean updateExecutionFill(Integer executionId, TradeServiceExecutionFillDTO fillDTO) {
        String url = baseUrl + "/api/v1/executions/" + executionId + "/fill";
        
        int attempts = 0;
        int maxAttempts = retryEnabled ? maxRetryAttempts : 1;
        
        while (attempts < maxAttempts) {
            attempts++;
            
            try {
                logger.debug("Updating execution fill in trade service (attempt {}): {}", attempts, url);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<TradeServiceExecutionFillDTO> request = new HttpEntity<>(fillDTO, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.PUT, request, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.debug("Successfully updated execution {} in trade service", executionId);
                    return true;
                } else {
                    logger.warn("Unexpected response code {} for execution {}", 
                            response.getStatusCode(), executionId);
                    return false;
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404) {
                    logger.warn("Execution {} not found in trade service", executionId);
                    return false;
                } else if (e.getStatusCode().value() == 409) {
                    logger.warn("Optimistic locking conflict for execution {} (attempt {})", executionId, attempts);
                    if (attempts < maxAttempts && retryEnabled) {
                        // Retry with fresh version
                        Optional<Integer> newVersion = getExecutionVersion(executionId);
                        if (newVersion.isPresent()) {
                            fillDTO.setVersion(newVersion.get());
                            logger.debug("Retrying with new version {} for execution {}", newVersion.get(), executionId);
                            continue;
                        } else {
                            logger.warn("Could not retrieve new version for retry of execution {}", executionId);
                            return false;
                        }
                    } else {
                        logger.error("Max retry attempts reached for execution {}", executionId);
                        return false;
                    }
                } else {
                    logger.error("HTTP error updating execution {} in trade service: {}", executionId, e.getMessage());
                    return false;
                }
            } catch (ResourceAccessException e) {
                logger.error("Network error updating execution {} in trade service: {}", executionId, e.getMessage());
                return false;
            } catch (Exception e) {
                logger.error("Unexpected error updating execution {} in trade service: {}", executionId, e.getMessage());
                return false;
            }
        }
        
        return false;
    }
} 