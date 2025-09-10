package org.kasbench.globeco_execution_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for handling error recovery and fallback mechanisms in batch processing.
 * Provides comprehensive error handling strategies including retry logic, fallback operations,
 * and transient failure recovery.
 */
@Service
public class ErrorRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(ErrorRecoveryService.class);

    private final ExecutionRepository executionRepository;
    private final BatchExecutionProperties batchProperties;
    private final BatchProcessingMetrics metrics;

    @Autowired
    public ErrorRecoveryService(ExecutionRepository executionRepository,
                              BatchExecutionProperties batchProperties,
                              BatchProcessingMetrics metrics) {
        this.executionRepository = executionRepository;
        this.batchProperties = batchProperties;
        this.metrics = metrics;
    }

    /**
     * Attempt bulk insert with fallback to individual inserts on failure.
     * This is the primary fallback mechanism for bulk operation failures.
     * 
     * @param executions List of executions to insert
     * @param context Batch processing context for error tracking
     * @return List of successfully inserted executions
     */
    public List<Execution> bulkInsertWithFallback(List<Execution> executions, BatchProcessingContext context) {
        logger.debug("Attempting bulk insert for {} executions", executions.size());
        
        try {
            // First attempt: bulk insert
            List<Execution> result = executionRepository.bulkInsert(executions);
            logger.debug("Bulk insert successful for {} executions", result.size());
            metrics.recordBulkInsertSuccess(executions.size());
            return result;
            
        } catch (Exception e) {
            logger.warn("Bulk insert failed, falling back to individual inserts: {}", e.getMessage());
            metrics.recordBulkInsertFailure(executions.size(), e.getClass().getSimpleName());
            
            // Fallback: individual inserts with error isolation
            return fallbackToIndividualInserts(executions, context, e);
        }
    }

    /**
     * Fallback mechanism that processes executions individually when bulk operations fail.
     * This provides error isolation so that valid executions can still be processed
     * even if some fail.
     * 
     * @param executions List of executions to process individually
     * @param context Batch processing context for error tracking
     * @param originalError The original bulk operation error
     * @return List of successfully inserted executions
     */
    private List<Execution> fallbackToIndividualInserts(List<Execution> executions, 
                                                       BatchProcessingContext context, 
                                                       Exception originalError) {
        logger.info("Processing {} executions individually as fallback", executions.size());
        
        List<Execution> successfulInserts = new ArrayList<>();
        
        for (int i = 0; i < executions.size(); i++) {
            Execution execution = executions.get(i);
            int originalIndex = findOriginalRequestIndex(context, execution, i);
            
            try {
                Execution savedExecution = insertWithRetry(execution);
                successfulInserts.add(savedExecution);
                context.recordDatabaseSuccess(originalIndex, savedExecution);
                logger.debug("Successfully saved execution at index {} individually", originalIndex);
                
            } catch (Exception e) {
                logger.error("Failed to save execution at index {} individually: {}", originalIndex, e.getMessage());
                
                // Create detailed error information
                DatabaseError dbError = new DatabaseError(
                    originalIndex,
                    execution,
                    e,
                    originalError,
                    "Individual insert failed after bulk insert failure"
                );
                
                context.recordDatabaseError(originalIndex, dbError);
                metrics.recordIndividualInsertFailure(e.getClass().getSimpleName());
            }
        }
        
        logger.info("Fallback processing completed: {}/{} executions successful", 
                   successfulInserts.size(), executions.size());
        
        return successfulInserts;
    }

    /**
     * Insert a single execution with retry logic for transient failures.
     * 
     * @param execution The execution to insert
     * @return The saved execution
     * @throws Exception if all retry attempts fail
     */
    @Transactional
    private Execution insertWithRetry(Execution execution) throws Exception {
        int maxRetries = batchProperties.getDatabase().getMaxRetries();
        long baseDelayMs = batchProperties.getDatabase().getRetryDelayMs();
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return executionRepository.saveAndFlush(execution);
                
            } catch (Exception e) {
                lastException = e;
                
                if (isTransientError(e) && attempt < maxRetries) {
                    long delayMs = calculateRetryDelay(baseDelayMs, attempt);
                    logger.debug("Transient error on attempt {}/{}, retrying in {}ms: {}", 
                               attempt, maxRetries, delayMs, e.getMessage());
                    
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    break;
                }
            }
        }
        
        throw new RuntimeException("Failed to insert execution after " + maxRetries + " attempts", lastException);
    }

    /**
     * Determine if an error is transient and worth retrying.
     * 
     * @param error The error to check
     * @return true if the error is likely transient
     */
    private boolean isTransientError(Exception error) {
        return error instanceof TransientDataAccessException ||
               error instanceof DeadlockLoserDataAccessException ||
               error instanceof QueryTimeoutException ||
               (error instanceof DataAccessException && 
                error.getMessage() != null && 
                (error.getMessage().contains("connection") || 
                 error.getMessage().contains("timeout") ||
                 error.getMessage().contains("deadlock")));
    }

    /**
     * Calculate retry delay with exponential backoff and jitter.
     * 
     * @param baseDelayMs Base delay in milliseconds
     * @param attempt Current attempt number (1-based)
     * @return Delay in milliseconds
     */
    private long calculateRetryDelay(long baseDelayMs, int attempt) {
        // Exponential backoff with jitter
        long exponentialDelay = baseDelayMs * (1L << (attempt - 1));
        long maxDelay = batchProperties.getDatabase().getMaxRetryDelayMs();
        long delay = Math.min(exponentialDelay, maxDelay);
        
        // Add jitter (±25%)
        long jitter = (long) (delay * 0.25 * (ThreadLocalRandom.current().nextDouble() - 0.5));
        return Math.max(delay + jitter, baseDelayMs);
    }

    /**
     * Recover from Kafka publishing failures by attempting to republish failed messages.
     * This method handles transient Kafka failures and provides detailed error reporting.
     * 
     * @param failedExecutions List of executions that failed to publish to Kafka
     * @param context Batch processing context
     * @return CompletableFuture that completes when recovery attempts are finished
     */
    public CompletableFuture<Void> recoverKafkaFailures(List<ExecutionDTO> failedExecutions, 
                                                       BatchProcessingContext context) {
        if (failedExecutions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("Attempting to recover {} failed Kafka publications", failedExecutions.size());
        
        return CompletableFuture.runAsync(() -> {
            for (ExecutionDTO execution : failedExecutions) {
                try {
                    // Attempt to republish with retry logic
                    // This would integrate with AsyncKafkaPublisher
                    logger.debug("Attempting to recover Kafka publication for execution {}", execution.getId());
                    
                    // For now, just log the recovery attempt
                    // In a full implementation, this would call AsyncKafkaPublisher.publishAsync
                    metrics.recordKafkaRecoveryAttempt();
                    
                } catch (Exception e) {
                    logger.error("Failed to recover Kafka publication for execution {}: {}", 
                               execution.getId(), e.getMessage());
                    metrics.recordKafkaRecoveryFailure();
                }
            }
        });
    }

    /**
     * Find the original request index for a given execution.
     * This is needed to map back from the validated executions list to the original request indices.
     */
    private int findOriginalRequestIndex(BatchProcessingContext context, Execution execution, int validExecutionIndex) {
        // Since we process executions in order and only include valid ones,
        // we need to map back to the original request index
        List<ExecutionPostDTO> originalRequests = context.getOriginalRequests();
        
        int validExecutionCount = 0;
        for (int i = 0; i < originalRequests.size(); i++) {
            if (!context.getValidationErrorIndices().contains(i)) {
                if (validExecutionCount == validExecutionIndex) {
                    return i;
                }
                validExecutionCount++;
            }
        }
        
        // Fallback: return the valid execution index if mapping fails
        return validExecutionIndex;
    }

    /**
     * Custom exception class for database errors with detailed context.
     */
    public static class DatabaseError extends Exception {
        private final int requestIndex;
        private final Execution execution;
        private final Exception originalError;
        private final Exception bulkOperationError;
        private final String context;
        private final OffsetDateTime errorTimestamp;

        public DatabaseError(int requestIndex, Execution execution, Exception originalError, 
                           Exception bulkOperationError, String context) {
            super(createErrorMessage(requestIndex, originalError, context));
            this.requestIndex = requestIndex;
            this.execution = execution;
            this.originalError = originalError;
            this.bulkOperationError = bulkOperationError;
            this.context = context;
            this.errorTimestamp = OffsetDateTime.now();
        }

        private static String createErrorMessage(int requestIndex, Exception originalError, String context) {
            return String.format("Database error at index %d: %s [Context: %s]", 
                                requestIndex, originalError.getMessage(), context);
        }

        public int getRequestIndex() { return requestIndex; }
        public Execution getExecution() { return execution; }
        public Exception getOriginalError() { return originalError; }
        public Exception getBulkOperationError() { return bulkOperationError; }
        public String getContext() { return context; }
        public OffsetDateTime getErrorTimestamp() { return errorTimestamp; }

        /**
         * Get detailed error information for debugging and monitoring.
         */
        public String getDetailedErrorInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append("Database Error Details:\n");
            sb.append("  Request Index: ").append(requestIndex).append("\n");
            sb.append("  Error Timestamp: ").append(errorTimestamp).append("\n");
            sb.append("  Context: ").append(context).append("\n");
            sb.append("  Original Error: ").append(originalError.getClass().getSimpleName())
              .append(" - ").append(originalError.getMessage()).append("\n");
            
            if (bulkOperationError != null) {
                sb.append("  Bulk Operation Error: ").append(bulkOperationError.getClass().getSimpleName())
                  .append(" - ").append(bulkOperationError.getMessage()).append("\n");
            }
            
            if (execution != null) {
                sb.append("  Execution Details: SecurityId=").append(execution.getSecurityId())
                  .append(", TradeType=").append(execution.getTradeType())
                  .append(", Quantity=").append(execution.getQuantity()).append("\n");
            }
            
            return sb.toString();
        }
    }
}