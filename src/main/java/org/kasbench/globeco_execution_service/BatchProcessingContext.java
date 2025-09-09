package org.kasbench.globeco_execution_service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Context class to track processing state across bulk operations and coordinate
 * between database operations and Kafka publishing.
 * 
 * This class provides comprehensive error tracking, result aggregation, and
 * coordination logic for batch execution processing.
 */
public class BatchProcessingContext {
    
    private final List<ExecutionPostDTO> originalRequests;
    private final List<Execution> validatedExecutions;
    private final List<ExecutionResultDTO> results;
    private final Map<Integer, Exception> validationErrors;
    private final Map<Integer, Exception> databaseErrors;
    private final Map<Integer, Exception> kafkaErrors;
    private final Set<Integer> successfulDatabaseOperations;
    private final Set<Integer> successfulKafkaOperations;
    private final AtomicInteger processedCount;
    private final OffsetDateTime processingStartTime;
    private OffsetDateTime processingEndTime;
    
    // Processing state tracking
    private volatile ProcessingPhase currentPhase;
    private volatile boolean processingComplete;
    
    /**
     * Enum representing the different phases of batch processing.
     */
    public enum ProcessingPhase {
        VALIDATION,
        DATABASE_OPERATIONS,
        KAFKA_PUBLISHING,
        RESULT_AGGREGATION,
        COMPLETED
    }
    
    /**
     * Constructor to initialize the batch processing context.
     * 
     * @param originalRequests The original list of execution requests
     */
    public BatchProcessingContext(List<ExecutionPostDTO> originalRequests) {
        this.originalRequests = new ArrayList<>(originalRequests);
        this.validatedExecutions = new ArrayList<>();
        this.results = new ArrayList<>();
        this.validationErrors = new ConcurrentHashMap<>();
        this.databaseErrors = new ConcurrentHashMap<>();
        this.kafkaErrors = new ConcurrentHashMap<>();
        this.successfulDatabaseOperations = ConcurrentHashMap.newKeySet();
        this.successfulKafkaOperations = ConcurrentHashMap.newKeySet();
        this.processedCount = new AtomicInteger(0);
        this.processingStartTime = OffsetDateTime.now();
        this.currentPhase = ProcessingPhase.VALIDATION;
        this.processingComplete = false;
        
        // Initialize results list with placeholders
        for (int i = 0; i < originalRequests.size(); i++) {
            results.add(null);
        }
    }
    
    /**
     * Add a validation error for a specific request index.
     * 
     * @param requestIndex The index of the request that failed validation
     * @param error The validation error
     */
    public void addValidationError(int requestIndex, Exception error) {
        validationErrors.put(requestIndex, error);
        updateResult(requestIndex, ExecutionResultDTO.failure(requestIndex, error.getMessage()));
    }
    
    /**
     * Add a validated execution for a specific request index.
     * 
     * @param requestIndex The index of the original request
     * @param execution The validated execution entity
     */
    public void addValidatedExecution(int requestIndex, Execution execution) {
        // Ensure the list is large enough
        while (validatedExecutions.size() <= requestIndex) {
            validatedExecutions.add(null);
        }
        validatedExecutions.set(requestIndex, execution);
    }
    
    /**
     * Record a successful database operation for a specific request index.
     * 
     * @param requestIndex The index of the request
     * @param savedExecution The saved execution entity
     */
    public void recordDatabaseSuccess(int requestIndex, Execution savedExecution) {
        successfulDatabaseOperations.add(requestIndex);
        // Convert to DTO and update result
        ExecutionDTO executionDTO = convertToDTO(savedExecution);
        updateResult(requestIndex, ExecutionResultDTO.success(requestIndex, executionDTO));
        processedCount.incrementAndGet();
    }
    
    /**
     * Record a database error for a specific request index.
     * 
     * @param requestIndex The index of the request
     * @param error The database error
     */
    public void recordDatabaseError(int requestIndex, Exception error) {
        databaseErrors.put(requestIndex, error);
        updateResult(requestIndex, ExecutionResultDTO.failure(requestIndex, 
            "Database error: " + error.getMessage()));
        processedCount.incrementAndGet();
    }
    
    /**
     * Record a successful Kafka operation for a specific request index.
     * 
     * @param requestIndex The index of the request
     */
    public void recordKafkaSuccess(int requestIndex) {
        successfulKafkaOperations.add(requestIndex);
    }
    
    /**
     * Record a Kafka error for a specific request index.
     * Note: Kafka errors don't affect the overall success status since
     * database persistence is the primary concern.
     * 
     * @param requestIndex The index of the request
     * @param error The Kafka error
     */
    public void recordKafkaError(int requestIndex, Exception error) {
        kafkaErrors.put(requestIndex, error);
        // Kafka errors are logged but don't change the result status
        // since database persistence is what matters for execution success
    }
    
    /**
     * Update the processing phase.
     * 
     * @param phase The new processing phase
     */
    public void setProcessingPhase(ProcessingPhase phase) {
        this.currentPhase = phase;
        if (phase == ProcessingPhase.COMPLETED) {
            this.processingComplete = true;
            this.processingEndTime = OffsetDateTime.now();
        }
    }
    
    /**
     * Get the list of executions that passed validation.
     * 
     * @return List of validated executions (may contain nulls for failed validations)
     */
    public List<Execution> getValidatedExecutions() {
        return new ArrayList<>(validatedExecutions);
    }
    
    /**
     * Get the list of executions that passed validation, excluding nulls.
     * 
     * @return List of validated executions without nulls
     */
    public List<Execution> getValidExecutionsOnly() {
        return validatedExecutions.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Get the final batch execution response.
     * 
     * @return BatchExecutionResponseDTO with aggregated results
     */
    public BatchExecutionResponseDTO getBatchResponse() {
        return BatchExecutionResponseDTO.fromResults(new ArrayList<>(results));
    }
    
    /**
     * Get processing statistics for monitoring and debugging.
     * 
     * @return Map containing various processing statistics
     */
    public Map<String, Object> getProcessingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", originalRequests.size());
        stats.put("validationErrors", validationErrors.size());
        stats.put("databaseErrors", databaseErrors.size());
        stats.put("kafkaErrors", kafkaErrors.size());
        stats.put("successfulDatabaseOperations", successfulDatabaseOperations.size());
        stats.put("successfulKafkaOperations", successfulKafkaOperations.size());
        stats.put("currentPhase", currentPhase.name());
        stats.put("processingComplete", processingComplete);
        stats.put("processingStartTime", processingStartTime);
        stats.put("processingEndTime", processingEndTime);
        
        if (processingEndTime != null) {
            long durationMs = java.time.Duration.between(processingStartTime, processingEndTime).toMillis();
            stats.put("processingDurationMs", durationMs);
        }
        
        return stats;
    }
    
    /**
     * Check if all database operations have been completed.
     * 
     * @return true if all database operations are complete
     */
    public boolean isDatabaseProcessingComplete() {
        int totalValidExecutions = (int) validatedExecutions.stream()
            .filter(Objects::nonNull)
            .count();
        return (successfulDatabaseOperations.size() + databaseErrors.size()) >= totalValidExecutions;
    }
    
    /**
     * Check if all Kafka operations have been completed.
     * 
     * @return true if all Kafka operations are complete
     */
    public boolean isKafkaProcessingComplete() {
        int totalSuccessfulDb = successfulDatabaseOperations.size();
        return (successfulKafkaOperations.size() + kafkaErrors.size()) >= totalSuccessfulDb;
    }
    
    /**
     * Get the indices of requests that had validation errors.
     * 
     * @return Set of request indices with validation errors
     */
    public Set<Integer> getValidationErrorIndices() {
        return new HashSet<>(validationErrors.keySet());
    }
    
    /**
     * Get the indices of requests that had database errors.
     * 
     * @return Set of request indices with database errors
     */
    public Set<Integer> getDatabaseErrorIndices() {
        return new HashSet<>(databaseErrors.keySet());
    }
    
    /**
     * Get the indices of requests that had Kafka errors.
     * 
     * @return Set of request indices with Kafka errors
     */
    public Set<Integer> getKafkaErrorIndices() {
        return new HashSet<>(kafkaErrors.keySet());
    }
    
    /**
     * Get the indices of requests that were successfully processed in the database.
     * 
     * @return Set of request indices with successful database operations
     */
    public Set<Integer> getSuccessfulDatabaseIndices() {
        return new HashSet<>(successfulDatabaseOperations);
    }
    
    /**
     * Get the indices of requests that were successfully published to Kafka.
     * 
     * @return Set of request indices with successful Kafka operations
     */
    public Set<Integer> getSuccessfulKafkaIndices() {
        return new HashSet<>(successfulKafkaOperations);
    }
    
    /**
     * Get all error details for debugging purposes.
     * 
     * @return Map containing all error information
     */
    public Map<String, Map<Integer, String>> getAllErrors() {
        Map<String, Map<Integer, String>> allErrors = new HashMap<>();
        
        Map<Integer, String> validationErrorMessages = validationErrors.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getMessage()
            ));
        allErrors.put("validation", validationErrorMessages);
        
        Map<Integer, String> databaseErrorMessages = databaseErrors.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getMessage()
            ));
        allErrors.put("database", databaseErrorMessages);
        
        Map<Integer, String> kafkaErrorMessages = kafkaErrors.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getMessage()
            ));
        allErrors.put("kafka", kafkaErrorMessages);
        
        return allErrors;
    }
    
    // Getters for read-only access
    public List<ExecutionPostDTO> getOriginalRequests() {
        return new ArrayList<>(originalRequests);
    }
    
    public List<ExecutionResultDTO> getResults() {
        return new ArrayList<>(results);
    }
    
    public ProcessingPhase getCurrentPhase() {
        return currentPhase;
    }
    
    public boolean isProcessingComplete() {
        return processingComplete;
    }
    
    public OffsetDateTime getProcessingStartTime() {
        return processingStartTime;
    }
    
    public OffsetDateTime getProcessingEndTime() {
        return processingEndTime;
    }
    
    public int getProcessedCount() {
        return processedCount.get();
    }
    
    /**
     * Private helper method to update a result at a specific index.
     * 
     * @param index The index to update
     * @param result The new result
     */
    private void updateResult(int index, ExecutionResultDTO result) {
        if (index >= 0 && index < results.size()) {
            results.set(index, result);
        }
    }
    
    /**
     * Private helper method to convert Execution entity to ExecutionDTO.
     * 
     * @param execution The execution entity
     * @return ExecutionDTO
     */
    private ExecutionDTO convertToDTO(Execution execution) {
        // Create SecurityDTO from the security ID
        // For now, we'll extract ticker from the security ID (first 4-5 chars typically)
        String securityId = execution.getSecurityId();
        String ticker = null;
        if (securityId != null && securityId.length() >= 4) {
            // Extract ticker from security ID (assuming first part is ticker)
            ticker = securityId.replaceAll("\\d", "").substring(0, Math.min(5, securityId.replaceAll("\\d", "").length()));
        }
        SecurityDTO security = new SecurityDTO(securityId, ticker);
        
        return new ExecutionDTO(
            execution.getId(),
            execution.getExecutionStatus(),
            execution.getTradeType(),
            execution.getDestination(),
            security,
            execution.getQuantity(),
            execution.getLimitPrice(),
            execution.getReceivedTimestamp(),
            execution.getSentTimestamp(),
            execution.getTradeServiceExecutionId(),
            execution.getQuantityFilled(),
            execution.getAveragePrice(),
            execution.getVersion()
        );
    }
}