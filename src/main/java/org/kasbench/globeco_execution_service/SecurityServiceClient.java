package org.kasbench.globeco_execution_service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Client interface for interacting with the Security Service.
 */
public interface SecurityServiceClient {
    /**
     * Get security information including ticker by security ID.
     * 
     * @param securityId The security identifier
     * @return Optional containing SecurityDTO if found, empty otherwise
     */
    Optional<SecurityDTO> getSecurityById(String securityId);
    
    /**
     * Get ticker symbol for a security ID with caching.
     * 
     * @param securityId The security identifier
     * @return Optional containing ticker if found, empty otherwise
     */
    Optional<String> getTickerBySecurityId(String securityId);
    
    /**
     * Get security ID by ticker symbol with caching.
     * 
     * @param ticker The ticker symbol
     * @return Optional containing security ID if found, empty otherwise
     */
    Optional<String> getSecurityIdByTicker(String ticker);
    
    /**
     * Get multiple securities by their IDs in a batch operation.
     * This method is optimized to reduce the number of HTTP calls.
     * 
     * @param securityIds Set of security IDs to fetch
     * @return Map of security ID to SecurityDTO for found securities
     */
    Map<String, SecurityDTO> getSecuritiesByIds(Set<String> securityIds);
} 