package org.kasbench.globeco_execution_service;

import java.util.Optional;

/**
 * Client interface for communicating with the trade service.
 */
public interface TradeServiceClient {
    
    /**
     * Retrieve the current version of an execution from the trade service.
     * @param executionId The execution ID in the trade service
     * @return Optional containing the version number if found, empty if not found or error occurred
     */
    Optional<Integer> getExecutionVersion(Integer executionId);
    
    /**
     * Update an execution in the trade service with fill information.
     * @param executionId The execution ID in the trade service
     * @param fillDTO The fill data to send
     * @return true if update was successful, false if failed
     */
    boolean updateExecutionFill(Integer executionId, TradeServiceExecutionFillDTO fillDTO);
} 