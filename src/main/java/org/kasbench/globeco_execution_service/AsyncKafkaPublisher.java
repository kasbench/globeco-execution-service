package org.kasbench.globeco_execution_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous Kafka publisher with retry logic and circuit breaker pattern.
 * Handles publishing execution messages to Kafka with configurable retry policies
 * and dead letter queue functionality.
 */
@Component
public class AsyncKafkaPublisher {
    private static final Logger logger = LoggerFactory.getLogger(AsyncKafkaPublisher.class);
    
    private final KafkaTemplate<String, ExecutionDTO> kafkaTemplate;
    private final BatchExecutionProperties batchProperties;
    private final BatchProcessingMetrics metrics;
    private final String ordersTopic;
    private final String deadLetterTopic;
    private final ScheduledExecutorService retryExecutor;
    private final Executor asyncExecutor;
    
    // Circuit breaker state
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private volatile CircuitBreakerState circuitState = CircuitBreakerState.CLOSED;
    
    // Metrics
    private final AtomicLong totalPublishAttempts = new AtomicLong(0);
    private final AtomicLong successfulPublishes = new AtomicLong(0);
    private final AtomicLong failedPublishes = new AtomicLong(0);
    private final AtomicLong retriedPublishes = new AtomicLong(0);
    private final AtomicLong deadLetterMessages = new AtomicLong(0);
    
    public AsyncKafkaPublisher(
            KafkaTemplate<String, ExecutionDTO> kafkaTemplate,
            BatchExecutionProperties batchProperties,
            BatchProcessingMetrics metrics,
            @Value("${kafka.topic.orders:orders}") String ordersTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.batchProperties = batchProperties;
        this.metrics = metrics;
        this.ordersTopic = ordersTopic;
        this.deadLetterTopic = ordersTopic + ".dlq";
        this.retryExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "kafka-retry-thread");
            t.setDaemon(true);
            return t;
        });
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "kafka-async-thread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Publishes a single execution message asynchronously with retry logic.
     * 
     * @param execution The execution DTO to publish
     * @return CompletableFuture that completes when the message is successfully sent or permanently fails
     */
    public CompletableFuture<PublishResult> publishAsync(ExecutionDTO execution) {
        if (!batchProperties.isEnableAsyncKafka()) {
            logger.debug("Async Kafka is disabled, skipping message publication for execution {}", execution.getId());
            return CompletableFuture.completedFuture(PublishResult.skipped(execution.getId()));
        }
        
        return publishWithRetry(execution, 0);
    }
    
    /**
     * Publishes multiple execution messages asynchronously.
     * 
     * @param executions List of execution DTOs to publish
     * @return CompletableFuture that completes when all messages are processed
     */
    public CompletableFuture<BatchPublishResult> publishBatchAsync(List<ExecutionDTO> executions) {
        if (!batchProperties.isEnableAsyncKafka()) {
            logger.debug("Async Kafka is disabled, skipping batch message publication for {} executions", executions.size());
            return CompletableFuture.completedFuture(BatchPublishResult.allSkipped(executions.size()));
        }
        
        List<CompletableFuture<PublishResult>> futures = executions.stream()
                .map(this::publishAsync)
                .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<PublishResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();
                    return BatchPublishResult.fromResults(results);
                });
    }
    
    /**
     * Internal method to handle publishing with retry logic.
     */
    private CompletableFuture<PublishResult> publishWithRetry(ExecutionDTO execution, int attemptNumber) {
        totalPublishAttempts.incrementAndGet();
        
        // Check circuit breaker
        if (isCircuitOpen()) {
            logger.warn("Circuit breaker is OPEN, failing fast for execution {}", execution.getId());
            failedPublishes.incrementAndGet();
            return CompletableFuture.completedFuture(
                PublishResult.failed(execution.getId(), "Circuit breaker is open", attemptNumber)
            );
        }
        
        CompletableFuture<PublishResult> future = new CompletableFuture<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                logger.debug("Publishing execution {} to Kafka (attempt {})", execution.getId(), attemptNumber + 1);
                
                OffsetDateTime startTime = OffsetDateTime.now();
                CompletableFuture<SendResult<String, ExecutionDTO>> sendFuture = 
                    kafkaTemplate.send(ordersTopic, execution.getId().toString(), execution);
                
                sendFuture.whenComplete((result, throwable) -> {
                    Duration duration = Duration.between(startTime, OffsetDateTime.now());
                    
                    if (throwable == null) {
                        // Success
                        logger.debug("Successfully published execution {} to Kafka on attempt {}", 
                            execution.getId(), attemptNumber + 1);
                        successfulPublishes.incrementAndGet();
                        metrics.recordKafkaPublishSuccess(duration);
                        onPublishSuccess();
                        future.complete(PublishResult.success(execution.getId(), attemptNumber));
                    } else {
                        // Failure
                        logger.warn("Failed to publish execution {} to Kafka on attempt {}: {}", 
                            execution.getId(), attemptNumber + 1, throwable.getMessage());
                        metrics.recordKafkaPublishFailure(duration, throwable.getClass().getSimpleName());
                        onPublishFailure();
                        handlePublishFailure(execution, attemptNumber, throwable, future);
                    }
                });
                
            } catch (Exception e) {
                logger.error("Unexpected error publishing execution {} to Kafka on attempt {}: {}", 
                    execution.getId(), attemptNumber + 1, e.getMessage(), e);
                Duration duration = Duration.between(OffsetDateTime.now(), OffsetDateTime.now()); // Immediate failure
                metrics.recordKafkaPublishFailure(duration, e.getClass().getSimpleName());
                onPublishFailure();
                handlePublishFailure(execution, attemptNumber, e, future);
            }
        }, asyncExecutor);
        
        return future;
    }
    
    /**
     * Handles publish failures and determines whether to retry or send to DLQ.
     */
    private void handlePublishFailure(ExecutionDTO execution, int attemptNumber, Throwable error, 
                                    CompletableFuture<PublishResult> future) {
        
        BatchExecutionProperties.KafkaRetryProperties retryProps = batchProperties.getKafka();
        
        if (attemptNumber < retryProps.getMaxAttempts() - 1) {
            // Schedule retry
            long delay = calculateRetryDelay(attemptNumber, retryProps);
            logger.info("Scheduling retry for execution {} in {}ms (attempt {} of {})", 
                execution.getId(), delay, attemptNumber + 1, retryProps.getMaxAttempts());
            
            retriedPublishes.incrementAndGet();
            metrics.recordKafkaRetry(attemptNumber + 1);
            
            retryExecutor.schedule(() -> {
                publishWithRetry(execution, attemptNumber + 1)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(result);
                        }
                    });
            }, delay, TimeUnit.MILLISECONDS);
            
        } else {
            // Max retries exceeded
            logger.error("Max retry attempts exceeded for execution {}, routing to dead letter queue", 
                execution.getId());
            failedPublishes.incrementAndGet();
            
            if (retryProps.isEnableDeadLetterQueue()) {
                sendToDeadLetterQueue(execution);
            }
            
            future.complete(PublishResult.failed(execution.getId(), error.getMessage(), attemptNumber));
        }
    }
    
    /**
     * Calculates the delay for the next retry attempt using exponential backoff.
     */
    private long calculateRetryDelay(int attemptNumber, BatchExecutionProperties.KafkaRetryProperties retryProps) {
        long delay = (long) (retryProps.getInitialDelay() * Math.pow(retryProps.getBackoffMultiplier(), attemptNumber));
        return Math.min(delay, retryProps.getMaxDelay());
    }
    
    /**
     * Sends a message to the dead letter queue.
     */
    private void sendToDeadLetterQueue(ExecutionDTO execution) {
        try {
            // Create a DLQ message with additional metadata
            DeadLetterMessage dlqMessage = new DeadLetterMessage(
                execution,
                OffsetDateTime.now(),
                "Max retry attempts exceeded",
                ordersTopic
            );
            
            kafkaTemplate.send(deadLetterTopic, execution.getId().toString(), execution)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        logger.info("Successfully sent execution {} to dead letter queue", execution.getId());
                        deadLetterMessages.incrementAndGet();
                    } else {
                        logger.error("Failed to send execution {} to dead letter queue: {}", 
                            execution.getId(), throwable.getMessage());
                    }
                });
                
        } catch (Exception e) {
            logger.error("Error sending execution {} to dead letter queue: {}", execution.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Handles successful publish events for circuit breaker management.
     */
    private void onPublishSuccess() {
        if (circuitState == CircuitBreakerState.HALF_OPEN) {
            logger.info("Circuit breaker transitioning from HALF_OPEN to CLOSED after successful publish");
            circuitState = CircuitBreakerState.CLOSED;
            failureCount.set(0);
        }
    }
    
    /**
     * Handles failed publish events for circuit breaker management.
     */
    private void onPublishFailure() {
        int failures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        BatchExecutionProperties.PerformanceProperties perfProps = batchProperties.getPerformance();
        
        if (failures >= perfProps.getCircuitBreakerFailureThreshold() && circuitState == CircuitBreakerState.CLOSED) {
            logger.warn("Circuit breaker transitioning from CLOSED to OPEN after {} failures", failures);
            circuitState = CircuitBreakerState.OPEN;
            lastFailureTime.set(System.currentTimeMillis());
            metrics.recordKafkaCircuitBreakerOpen("failure_threshold_exceeded");
        }
    }
    
    /**
     * Checks if the circuit breaker is open and should block requests.
     */
    private boolean isCircuitOpen() {
        if (circuitState == CircuitBreakerState.CLOSED) {
            return false;
        }
        
        if (circuitState == CircuitBreakerState.OPEN) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
            long recoveryTimeout = batchProperties.getPerformance().getCircuitBreakerRecoveryTimeout();
            
            if (timeSinceLastFailure >= recoveryTimeout) {
                logger.info("Circuit breaker transitioning from OPEN to HALF_OPEN for recovery attempt");
                circuitState = CircuitBreakerState.HALF_OPEN;
                return false;
            }
            return true;
        }
        
        // HALF_OPEN state - allow one request through
        return false;
    }
    
    /**
     * Gets current publishing metrics.
     */
    public PublishMetrics getMetrics() {
        return new PublishMetrics(
            totalPublishAttempts.get(),
            successfulPublishes.get(),
            failedPublishes.get(),
            retriedPublishes.get(),
            deadLetterMessages.get(),
            circuitState,
            failureCount.get()
        );
    }
    
    /**
     * Resets the circuit breaker to closed state (for testing/admin purposes).
     */
    public void resetCircuitBreaker() {
        logger.info("Manually resetting circuit breaker to CLOSED state");
        circuitState = CircuitBreakerState.CLOSED;
        failureCount.set(0);
        lastFailureTime.set(0);
    }
    
    /**
     * Circuit breaker states.
     */
    public enum CircuitBreakerState {
        CLOSED,    // Normal operation
        OPEN,      // Blocking requests due to failures
        HALF_OPEN  // Testing if service has recovered
    }
    
    /**
     * Result of a single message publish attempt.
     */
    public static class PublishResult {
        private final Integer executionId;
        private final boolean success;
        private final boolean skipped;
        private final String errorMessage;
        private final int attemptCount;
        
        private PublishResult(Integer executionId, boolean success, boolean skipped, String errorMessage, int attemptCount) {
            this.executionId = executionId;
            this.success = success;
            this.skipped = skipped;
            this.errorMessage = errorMessage;
            this.attemptCount = attemptCount;
        }
        
        public static PublishResult success(Integer executionId, int attemptCount) {
            return new PublishResult(executionId, true, false, null, attemptCount + 1);
        }
        
        public static PublishResult failed(Integer executionId, String errorMessage, int attemptCount) {
            return new PublishResult(executionId, false, false, errorMessage, attemptCount + 1);
        }
        
        public static PublishResult skipped(Integer executionId) {
            return new PublishResult(executionId, false, true, "Async Kafka disabled", 0);
        }
        
        // Getters
        public Integer getExecutionId() { return executionId; }
        public boolean isSuccess() { return success; }
        public boolean isSkipped() { return skipped; }
        public String getErrorMessage() { return errorMessage; }
        public int getAttemptCount() { return attemptCount; }
    }
    
    /**
     * Result of a batch publish operation.
     */
    public static class BatchPublishResult {
        private final int totalMessages;
        private final int successfulMessages;
        private final int failedMessages;
        private final int skippedMessages;
        private final List<PublishResult> results;
        
        private BatchPublishResult(int totalMessages, int successfulMessages, int failedMessages, 
                                 int skippedMessages, List<PublishResult> results) {
            this.totalMessages = totalMessages;
            this.successfulMessages = successfulMessages;
            this.failedMessages = failedMessages;
            this.skippedMessages = skippedMessages;
            this.results = results;
        }
        
        public static BatchPublishResult fromResults(List<PublishResult> results) {
            int successful = 0;
            int failed = 0;
            int skipped = 0;
            
            for (PublishResult result : results) {
                if (result.isSkipped()) {
                    skipped++;
                } else if (result.isSuccess()) {
                    successful++;
                } else {
                    failed++;
                }
            }
            
            return new BatchPublishResult(results.size(), successful, failed, skipped, results);
        }
        
        public static BatchPublishResult allSkipped(int count) {
            return new BatchPublishResult(count, 0, 0, count, List.of());
        }
        
        // Getters
        public int getTotalMessages() { return totalMessages; }
        public int getSuccessfulMessages() { return successfulMessages; }
        public int getFailedMessages() { return failedMessages; }
        public int getSkippedMessages() { return skippedMessages; }
        public List<PublishResult> getResults() { return results; }
    }
    
    /**
     * Publishing metrics for monitoring.
     */
    public static class PublishMetrics {
        private final long totalAttempts;
        private final long successfulPublishes;
        private final long failedPublishes;
        private final long retriedPublishes;
        private final long deadLetterMessages;
        private final CircuitBreakerState circuitState;
        private final int currentFailureCount;
        
        public PublishMetrics(long totalAttempts, long successfulPublishes, long failedPublishes,
                            long retriedPublishes, long deadLetterMessages, CircuitBreakerState circuitState,
                            int currentFailureCount) {
            this.totalAttempts = totalAttempts;
            this.successfulPublishes = successfulPublishes;
            this.failedPublishes = failedPublishes;
            this.retriedPublishes = retriedPublishes;
            this.deadLetterMessages = deadLetterMessages;
            this.circuitState = circuitState;
            this.currentFailureCount = currentFailureCount;
        }
        
        // Getters
        public long getTotalAttempts() { return totalAttempts; }
        public long getSuccessfulPublishes() { return successfulPublishes; }
        public long getFailedPublishes() { return failedPublishes; }
        public long getRetriedPublishes() { return retriedPublishes; }
        public long getDeadLetterMessages() { return deadLetterMessages; }
        public CircuitBreakerState getCircuitState() { return circuitState; }
        public int getCurrentFailureCount() { return currentFailureCount; }
        
        public double getSuccessRate() {
            return totalAttempts > 0 ? (double) successfulPublishes / totalAttempts : 0.0;
        }
    }
    
    /**
     * Dead letter message wrapper.
     */
    private static class DeadLetterMessage {
        private final ExecutionDTO originalMessage;
        private final OffsetDateTime failedAt;
        private final String failureReason;
        private final String originalTopic;
        
        public DeadLetterMessage(ExecutionDTO originalMessage, OffsetDateTime failedAt, 
                               String failureReason, String originalTopic) {
            this.originalMessage = originalMessage;
            this.failedAt = failedAt;
            this.failureReason = failureReason;
            this.originalTopic = originalTopic;
        }
        
        // Getters
        public ExecutionDTO getOriginalMessage() { return originalMessage; }
        public OffsetDateTime getFailedAt() { return failedAt; }
        public String getFailureReason() { return failureReason; }
        public String getOriginalTopic() { return originalTopic; }
    }
}