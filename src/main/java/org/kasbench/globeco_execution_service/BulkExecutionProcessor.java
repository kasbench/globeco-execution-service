package org.kasbench.globeco_execution_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service component for processing bulk execution operations.
 * Handles validation, preparation, and batch splitting for optimal database performance.
 */
@Service
public class BulkExecutionProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BulkExecutionProcessor.class);

    private final BatchExecutionProperties batchProperties;
    private final BatchProcessingMetrics metrics;

    @Autowired
    public BulkExecutionProcessor(BatchExecutionProperties batchProperties, 
                                BatchProcessingMetrics metrics) {
        this.batchProperties = batchProperties;
        this.metrics = metrics;
    }

    /**
     * Process a batch of execution requests with validation and preparation.
     * 
     * @param executionRequests List of execution requests to process
     * @return BatchProcessingContext containing validated executions and any validation errors
     */
    public BatchProcessingContext processBatch(List<ExecutionPostDTO> executionRequests) {
        logger.debug("Processing batch of {} execution requests", executionRequests.size());
        
        OffsetDateTime startTime = OffsetDateTime.now();
        BatchProcessingContext context = new BatchProcessingContext(executionRequests);
        
        try {
            // Validate all executions first
            validateExecutions(executionRequests, context);
            
            // Prepare valid executions for bulk operations
            prepareValidExecutions(context);
            
            // Split into optimal batch sizes if needed
            splitIntoBatches(context);
            
            // Record validation metrics
            Duration validationDuration = Duration.between(startTime, OffsetDateTime.now());
            int validCount = context.getValidatedExecutions().size();
            int errorCount = context.getValidationErrors().size();
            
            logger.debug("Batch processing completed: {} valid, {} invalid executions in {}ms", 
                        validCount, errorCount, validationDuration.toMillis());
            
            return context;
            
        } catch (Exception e) {
            logger.error("Error during batch processing: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Validate individual executions and collect validation errors.
     */
    private void validateExecutions(List<ExecutionPostDTO> requests, BatchProcessingContext context) {
        for (int i = 0; i < requests.size(); i++) {
            ExecutionPostDTO request = requests.get(i);
            try {
                validateSingleExecution(request, i);
                // If validation passes, add to results as pending
                context.addResult(ExecutionResultDTO.success(i, null));
            } catch (ValidationException e) {
                logger.debug("Validation failed for execution at index {}: {}", i, e.getMessage());
                context.addValidationError(i, e);
                context.addResult(ExecutionResultDTO.failure(i, e.getMessage()));
            }
        }
    }

    /**
     * Validate a single execution request.
     */
    private void validateSingleExecution(ExecutionPostDTO request, int index) throws ValidationException {
        if (request == null) {
            throw new ValidationException("Execution request cannot be null");
        }

        // Validate required fields
        if (request.getExecutionStatus() == null || request.getExecutionStatus().trim().isEmpty()) {
            throw new ValidationException("Execution status is required");
        }

        if (request.getTradeType() == null || request.getTradeType().trim().isEmpty()) {
            throw new ValidationException("Trade type is required");
        }

        if (request.getDestination() == null || request.getDestination().trim().isEmpty()) {
            throw new ValidationException("Destination is required");
        }

        if (request.getSecurityId() == null || request.getSecurityId().trim().isEmpty()) {
            throw new ValidationException("Security ID is required");
        }

        if (request.getQuantity() == null) {
            throw new ValidationException("Quantity is required");
        }

        // Validate field lengths and formats
        if (request.getExecutionStatus().length() > 20) {
            throw new ValidationException("Execution status cannot exceed 20 characters");
        }

        if (request.getTradeType().length() > 10) {
            throw new ValidationException("Trade type cannot exceed 10 characters");
        }

        if (request.getDestination().length() > 20) {
            throw new ValidationException("Destination cannot exceed 20 characters");
        }

        if (request.getSecurityId().length() > 24) {
            throw new ValidationException("Security ID cannot exceed 24 characters");
        }

        // Validate business rules
        if (request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Quantity must be greater than zero");
        }

        if (request.getLimitPrice() != null && request.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Limit price must be greater than zero when specified");
        }

        // Validate trade type values
        if (!isValidTradeType(request.getTradeType())) {
            throw new ValidationException("Invalid trade type. Must be BUY or SELL");
        }

        // Validate execution status values
        if (!isValidExecutionStatus(request.getExecutionStatus())) {
            throw new ValidationException("Invalid execution status. Must be NEW, PENDING, FILLED, CANCELLED, or REJECTED");
        }
    }

    /**
     * Prepare valid executions by converting DTOs to entities.
     */
    private void prepareValidExecutions(BatchProcessingContext context) {
        List<Execution> validatedExecutions = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (int i = 0; i < context.getOriginalRequests().size(); i++) {
            if (!context.hasValidationError(i)) {
                ExecutionPostDTO request = context.getOriginalRequests().get(i);
                Execution execution = convertToExecution(request, now);
                validatedExecutions.add(execution);
            }
        }

        context.setValidatedExecutions(validatedExecutions);
    }

    /**
     * Split large batches into optimal sizes for database operations.
     */
    private void splitIntoBatches(BatchProcessingContext context) {
        List<Execution> allExecutions = context.getValidatedExecutions();
        int batchSize = batchProperties.getBulkInsertBatchSize();
        
        if (allExecutions.size() <= batchSize) {
            // No splitting needed
            context.setExecutionBatches(List.of(allExecutions));
            return;
        }

        // Split into multiple batches
        List<List<Execution>> batches = new ArrayList<>();
        for (int i = 0; i < allExecutions.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allExecutions.size());
            batches.add(new ArrayList<>(allExecutions.subList(i, endIndex)));
        }

        context.setExecutionBatches(batches);
        logger.debug("Split {} executions into {} batches of max size {}", 
                    allExecutions.size(), batches.size(), batchSize);
    }

    /**
     * Convert ExecutionPostDTO to Execution entity.
     */
    private Execution convertToExecution(ExecutionPostDTO dto, OffsetDateTime receivedTimestamp) {
        Execution execution = new Execution();
        execution.setExecutionStatus(dto.getExecutionStatus());
        execution.setTradeType(dto.getTradeType());
        execution.setDestination(dto.getDestination());
        execution.setSecurityId(dto.getSecurityId());
        execution.setQuantity(dto.getQuantity());
        execution.setLimitPrice(dto.getLimitPrice());
        execution.setReceivedTimestamp(receivedTimestamp);
        execution.setTradeServiceExecutionId(dto.getTradeServiceExecutionId());
        execution.setQuantityFilled(BigDecimal.ZERO);
        execution.setAveragePrice(BigDecimal.ZERO);
        execution.setVersion(dto.getVersion() != null ? dto.getVersion() : 0);
        return execution;
    }

    /**
     * Validate trade type values.
     */
    private boolean isValidTradeType(String tradeType) {
        return "BUY".equals(tradeType) || "SELL".equals(tradeType);
    }

    /**
     * Validate execution status values.
     */
    private boolean isValidExecutionStatus(String status) {
        return "NEW".equals(status) || "PENDING".equals(status) || 
               "FILLED".equals(status) || "CANCELLED".equals(status) || 
               "REJECTED".equals(status);
    }

    /**
     * Custom exception for validation errors.
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    /**
     * Context class to track batch processing state.
     */
    public static class BatchProcessingContext {
        private final List<ExecutionPostDTO> originalRequests;
        private List<Execution> validatedExecutions = new ArrayList<>();
        private List<List<Execution>> executionBatches = new ArrayList<>();
        private final Map<Integer, ValidationException> validationErrors = new HashMap<>();
        private final List<ExecutionResultDTO> results = new ArrayList<>();

        public BatchProcessingContext(List<ExecutionPostDTO> originalRequests) {
            this.originalRequests = originalRequests;
        }

        public List<ExecutionPostDTO> getOriginalRequests() {
            return originalRequests;
        }

        public List<Execution> getValidatedExecutions() {
            return validatedExecutions;
        }

        public void setValidatedExecutions(List<Execution> validatedExecutions) {
            this.validatedExecutions = validatedExecutions;
        }

        public List<List<Execution>> getExecutionBatches() {
            return executionBatches;
        }

        public void setExecutionBatches(List<List<Execution>> executionBatches) {
            this.executionBatches = executionBatches;
        }

        public Map<Integer, ValidationException> getValidationErrors() {
            return validationErrors;
        }

        public void addValidationError(int index, ValidationException error) {
            validationErrors.put(index, error);
        }

        public boolean hasValidationError(int index) {
            return validationErrors.containsKey(index);
        }

        public List<ExecutionResultDTO> getResults() {
            return results;
        }

        public void addResult(ExecutionResultDTO result) {
            results.add(result);
        }

        public void updateResult(int index, ExecutionResultDTO result) {
            if (index >= 0 && index < results.size()) {
                results.set(index, result);
            }
        }

        public int getTotalRequested() {
            return originalRequests.size();
        }

        public int getValidCount() {
            return validatedExecutions.size();
        }

        public int getErrorCount() {
            return validationErrors.size();
        }
    }
}