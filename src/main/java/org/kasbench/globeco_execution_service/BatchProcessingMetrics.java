package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Comprehensive metrics collection for batch processing performance.
 * Tracks batch processing duration, throughput, error rates, database operations,
 * and Kafka publishing metrics.
 */
@Component
public class BatchProcessingMetrics {
    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingMetrics.class);
    
    private final MeterRegistry meterRegistry;
    
    // Batch Processing Metrics
    private final Timer batchProcessingTimer;
    private final Counter batchRequestsCounter;
    private final Counter batchSuccessCounter;
    private final Counter batchFailureCounter;
    private final Counter executionProcessedCounter;
    private final Counter executionSuccessCounter;
    private final Counter executionFailureCounter;
    private final DistributionSummary batchSizeDistribution;
    
    // Database Operation Metrics
    private final Timer bulkInsertTimer;
    private final Timer bulkUpdateTimer;
    private final Counter databaseOperationCounter;
    private final Counter databaseErrorCounter;
    private final DistributionSummary bulkOperationSizeDistribution;
    
    // Connection Pool Metrics
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong maxConnections = new AtomicLong(0);
    private final AtomicLong connectionWaitTime = new AtomicLong(0);
    
    // Kafka Publishing Metrics
    private final Timer kafkaPublishTimer;
    private final Counter kafkaPublishSuccessCounter;
    private final Counter kafkaPublishFailureCounter;
    private final Counter kafkaRetryCounter;
    private final Counter kafkaCircuitBreakerCounter;
    
    // Performance Tracking
    private final DoubleAdder totalProcessingTime = new DoubleAdder();
    private final AtomicLong totalBatches = new AtomicLong(0);
    private final AtomicLong totalExecutions = new AtomicLong(0);
    
    public BatchProcessingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize batch processing metrics
        this.batchProcessingTimer = Timer.builder("batch.processing.duration")
                .description("Time taken to process batch execution requests")
                .register(meterRegistry);
                
        this.batchRequestsCounter = Counter.builder("batch.requests.total")
                .description("Total number of batch execution requests")
                .register(meterRegistry);
                
        this.batchSuccessCounter = Counter.builder("batch.requests.success")
                .description("Number of successful batch execution requests")
                .register(meterRegistry);
                
        this.batchFailureCounter = Counter.builder("batch.requests.failure")
                .description("Number of failed batch execution requests")
                .register(meterRegistry);
                
        this.executionProcessedCounter = Counter.builder("batch.executions.processed")
                .description("Total number of individual executions processed")
                .register(meterRegistry);
                
        this.executionSuccessCounter = Counter.builder("batch.executions.success")
                .description("Number of successful individual executions")
                .register(meterRegistry);
                
        this.executionFailureCounter = Counter.builder("batch.executions.failure")
                .description("Number of failed individual executions")
                .register(meterRegistry);
                
        this.batchSizeDistribution = DistributionSummary.builder("batch.size")
                .description("Distribution of batch sizes")
                .register(meterRegistry);
        
        // Initialize database operation metrics
        this.bulkInsertTimer = Timer.builder("database.bulk.insert.duration")
                .description("Time taken for bulk insert operations")
                .register(meterRegistry);
                
        this.bulkUpdateTimer = Timer.builder("database.bulk.update.duration")
                .description("Time taken for bulk update operations")
                .register(meterRegistry);
                
        this.databaseOperationCounter = Counter.builder("database.operations.total")
                .description("Total number of database operations")
                .register(meterRegistry);
                
        this.databaseErrorCounter = Counter.builder("database.operations.error")
                .description("Number of database operation errors")
                .register(meterRegistry);
                
        this.bulkOperationSizeDistribution = DistributionSummary.builder("database.bulk.operation.size")
                .description("Distribution of bulk operation sizes")
                .register(meterRegistry);
        
        // Initialize connection pool gauges
        Gauge.builder("database.connection.pool.active", this, BatchProcessingMetrics::getActiveConnections)
                .description("Number of active database connections")
                .register(meterRegistry);
                
        Gauge.builder("database.connection.pool.max", this, BatchProcessingMetrics::getMaxConnections)
                .description("Maximum number of database connections")
                .register(meterRegistry);
                
        Gauge.builder("database.connection.pool.utilization", this, BatchProcessingMetrics::getConnectionUtilization)
                .description("Database connection pool utilization percentage")
                .register(meterRegistry);
        
        // Initialize Kafka publishing metrics
        this.kafkaPublishTimer = Timer.builder("kafka.publish.duration")
                .description("Time taken for Kafka message publishing")
                .register(meterRegistry);
                
        this.kafkaPublishSuccessCounter = Counter.builder("kafka.publish.success")
                .description("Number of successful Kafka message publishes")
                .register(meterRegistry);
                
        this.kafkaPublishFailureCounter = Counter.builder("kafka.publish.failure")
                .description("Number of failed Kafka message publishes")
                .register(meterRegistry);
                
        this.kafkaRetryCounter = Counter.builder("kafka.publish.retry")
                .description("Number of Kafka publish retry attempts")
                .register(meterRegistry);
                
        this.kafkaCircuitBreakerCounter = Counter.builder("kafka.circuit.breaker.open")
                .description("Number of times Kafka circuit breaker opened")
                .register(meterRegistry);
        
        // Initialize performance gauges
        Gauge.builder("batch.processing.throughput", this, BatchProcessingMetrics::getThroughput)
                .description("Batch processing throughput (executions per second)")
                .register(meterRegistry);
                
        Gauge.builder("batch.processing.average.duration", this, BatchProcessingMetrics::getAverageProcessingTime)
                .description("Average batch processing duration in seconds")
                .register(meterRegistry);
                
        Gauge.builder("batch.processing.success.rate", this, BatchProcessingMetrics::getSuccessRate)
                .description("Batch processing success rate (0.0 to 1.0)")
                .register(meterRegistry);
    }
    
    // Batch Processing Metrics Methods
    
    /**
     * Records the start of a batch processing operation.
     * @param batchSize The size of the batch being processed
     * @return Timer.Sample for measuring duration
     */
    public Timer.Sample startBatchProcessing(int batchSize) {
        batchRequestsCounter.increment();
        batchSizeDistribution.record(batchSize);
        totalBatches.incrementAndGet();
        totalExecutions.addAndGet(batchSize);
        
        logger.debug("Started batch processing for {} executions", batchSize);
        return Timer.start(meterRegistry);
    }
    
    /**
     * Records the completion of a batch processing operation.
     * @param sample The timer sample from startBatchProcessing
     * @param successful Whether the batch was successful
     * @param processedCount Number of executions processed
     * @param successCount Number of successful executions
     */
    public void recordBatchProcessingComplete(Timer.Sample sample, boolean successful, 
                                            int processedCount, int successCount) {
        long durationNanos = sample.stop(batchProcessingTimer);
        double durationSeconds = durationNanos / 1_000_000_000.0;
        totalProcessingTime.add(durationSeconds);
        
        if (successful) {
            batchSuccessCounter.increment();
        } else {
            batchFailureCounter.increment();
        }
        
        executionProcessedCounter.increment(processedCount);
        executionSuccessCounter.increment(successCount);
        executionFailureCounter.increment(processedCount - successCount);
        
        logger.debug("Completed batch processing in {}ns: {}/{} executions successful", 
                    durationNanos, successCount, processedCount);
    }
    
    // Database Operation Metrics Methods
    
    /**
     * Records a bulk insert operation.
     * @param recordCount Number of records inserted
     * @param duration Duration of the operation
     */
    public void recordBulkInsert(int recordCount, Duration duration) {
        bulkInsertTimer.record(duration);
        databaseOperationCounter.increment();
        bulkOperationSizeDistribution.record(recordCount);
        
        logger.debug("Recorded bulk insert: {} records in {}ms", recordCount, duration.toMillis());
    }
    
    /**
     * Records a bulk update operation.
     * @param recordCount Number of records updated
     * @param duration Duration of the operation
     */
    public void recordBulkUpdate(int recordCount, Duration duration) {
        bulkUpdateTimer.record(duration);
        databaseOperationCounter.increment();
        bulkOperationSizeDistribution.record(recordCount);
        
        logger.debug("Recorded bulk update: {} records in {}ms", recordCount, duration.toMillis());
    }
    
    /**
     * Records a database operation error.
     * @param operation The type of operation that failed
     * @param errorType The type of error
     */
    public void recordDatabaseError(String operation, String errorType) {
        databaseErrorCounter.increment();
        logger.debug("Recorded database error: {} - {}", operation, errorType);
    }
    
    // Connection Pool Metrics Methods
    
    /**
     * Updates connection pool metrics.
     * @param active Number of active connections
     * @param max Maximum number of connections
     * @param waitTime Average wait time for connections
     */
    public void updateConnectionPoolMetrics(long active, long max, long waitTime) {
        this.activeConnections.set(active);
        this.maxConnections.set(max);
        this.connectionWaitTime.set(waitTime);
    }
    
    public double getActiveConnections() {
        return activeConnections.get();
    }
    
    public double getMaxConnections() {
        return maxConnections.get();
    }
    
    public double getConnectionUtilization() {
        long max = maxConnections.get();
        return max > 0 ? (double) activeConnections.get() / max * 100.0 : 0.0;
    }
    
    // Kafka Publishing Metrics Methods
    
    /**
     * Records a successful Kafka message publish.
     * @param duration Duration of the publish operation
     */
    public void recordKafkaPublishSuccess(Duration duration) {
        kafkaPublishTimer.record(duration);
        kafkaPublishSuccessCounter.increment();
        
        logger.debug("Recorded successful Kafka publish in {}ms", duration.toMillis());
    }
    
    /**
     * Records a failed Kafka message publish.
     * @param duration Duration of the failed publish attempt
     * @param errorType Type of error that occurred
     */
    public void recordKafkaPublishFailure(Duration duration, String errorType) {
        kafkaPublishTimer.record(duration);
        kafkaPublishFailureCounter.increment();
        
        logger.debug("Recorded failed Kafka publish in {}ms: {}", duration.toMillis(), errorType);
    }
    
    /**
     * Records a Kafka publish retry attempt.
     * @param attemptNumber The retry attempt number
     */
    public void recordKafkaRetry(int attemptNumber) {
        kafkaRetryCounter.increment();
        logger.debug("Recorded Kafka retry attempt: {}", attemptNumber);
    }
    
    /**
     * Records when the Kafka circuit breaker opens.
     * @param reason Reason for circuit breaker opening
     */
    public void recordKafkaCircuitBreakerOpen(String reason) {
        kafkaCircuitBreakerCounter.increment();
        logger.warn("Kafka circuit breaker opened: {}", reason);
    }
    
    // Performance Calculation Methods
    
    /**
     * Calculates current throughput (executions per second).
     */
    public double getThroughput() {
        double totalTime = totalProcessingTime.sum();
        long executions = totalExecutions.get();
        
        return totalTime > 0 ? executions / totalTime : 0.0;
    }
    
    /**
     * Calculates average processing time per batch.
     */
    public double getAverageProcessingTime() {
        double totalTime = totalProcessingTime.sum();
        long batches = totalBatches.get();
        
        return batches > 0 ? totalTime / batches : 0.0;
    }
    
    /**
     * Calculates batch processing success rate.
     */
    public double getSuccessRate() {
        double total = batchRequestsCounter.count();
        double successful = batchSuccessCounter.count();
        
        return total > 0 ? successful / total : 0.0;
    }
    
    /**
     * Gets comprehensive metrics summary for monitoring dashboards.
     */
    public MetricsSummary getMetricsSummary() {
        return new MetricsSummary(
            batchRequestsCounter.count(),
            batchSuccessCounter.count(),
            batchFailureCounter.count(),
            executionProcessedCounter.count(),
            executionSuccessCounter.count(),
            executionFailureCounter.count(),
            getThroughput(),
            getAverageProcessingTime(),
            getSuccessRate(),
            getConnectionUtilization(),
            kafkaPublishSuccessCounter.count(),
            kafkaPublishFailureCounter.count(),
            kafkaRetryCounter.count()
        );
    }
    
    /**
     * Resets all metrics (primarily for testing purposes).
     */
    public void resetMetrics() {
        // Note: Micrometer counters and timers cannot be reset directly
        // This method is mainly for resetting internal state
        totalProcessingTime.reset();
        totalBatches.set(0);
        totalExecutions.set(0);
        activeConnections.set(0);
        maxConnections.set(0);
        connectionWaitTime.set(0);
        
        logger.info("Batch processing metrics reset");
    }
    
    /**
     * Comprehensive metrics summary for monitoring and alerting.
     */
    public static class MetricsSummary {
        private final double totalBatchRequests;
        private final double successfulBatchRequests;
        private final double failedBatchRequests;
        private final double totalExecutionsProcessed;
        private final double successfulExecutions;
        private final double failedExecutions;
        private final double throughput;
        private final double averageProcessingTime;
        private final double successRate;
        private final double connectionUtilization;
        private final double kafkaSuccessfulPublishes;
        private final double kafkaFailedPublishes;
        private final double kafkaRetryAttempts;
        
        public MetricsSummary(double totalBatchRequests, double successfulBatchRequests, 
                            double failedBatchRequests, double totalExecutionsProcessed,
                            double successfulExecutions, double failedExecutions,
                            double throughput, double averageProcessingTime, double successRate,
                            double connectionUtilization, double kafkaSuccessfulPublishes,
                            double kafkaFailedPublishes, double kafkaRetryAttempts) {
            this.totalBatchRequests = totalBatchRequests;
            this.successfulBatchRequests = successfulBatchRequests;
            this.failedBatchRequests = failedBatchRequests;
            this.totalExecutionsProcessed = totalExecutionsProcessed;
            this.successfulExecutions = successfulExecutions;
            this.failedExecutions = failedExecutions;
            this.throughput = throughput;
            this.averageProcessingTime = averageProcessingTime;
            this.successRate = successRate;
            this.connectionUtilization = connectionUtilization;
            this.kafkaSuccessfulPublishes = kafkaSuccessfulPublishes;
            this.kafkaFailedPublishes = kafkaFailedPublishes;
            this.kafkaRetryAttempts = kafkaRetryAttempts;
        }
        
        // Getters
        public double getTotalBatchRequests() { return totalBatchRequests; }
        public double getSuccessfulBatchRequests() { return successfulBatchRequests; }
        public double getFailedBatchRequests() { return failedBatchRequests; }
        public double getTotalExecutionsProcessed() { return totalExecutionsProcessed; }
        public double getSuccessfulExecutions() { return successfulExecutions; }
        public double getFailedExecutions() { return failedExecutions; }
        public double getThroughput() { return throughput; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public double getSuccessRate() { return successRate; }
        public double getConnectionUtilization() { return connectionUtilization; }
        public double getKafkaSuccessfulPublishes() { return kafkaSuccessfulPublishes; }
        public double getKafkaFailedPublishes() { return kafkaFailedPublishes; }
        public double getKafkaRetryAttempts() { return kafkaRetryAttempts; }
        
        /**
         * Determines if the system is performing within healthy parameters.
         */
        public boolean isHealthy() {
            return successRate > 0.95 && // 95% success rate
                   connectionUtilization < 80.0 && // Connection pool under 80%
                   (kafkaFailedPublishes == 0 || 
                    kafkaSuccessfulPublishes / (kafkaSuccessfulPublishes + kafkaFailedPublishes) > 0.90); // 90% Kafka success
        }
        
        @Override
        public String toString() {
            return String.format(
                "MetricsSummary{batches=%.0f(%.0f success, %.0f failed), " +
                "executions=%.0f(%.0f success, %.0f failed), " +
                "throughput=%.2f/s, avgTime=%.2fs, successRate=%.2f%%, " +
                "connUtil=%.1f%%, kafka=%.0f/%.0f, retries=%.0f}",
                totalBatchRequests, successfulBatchRequests, failedBatchRequests,
                totalExecutionsProcessed, successfulExecutions, failedExecutions,
                throughput, averageProcessingTime, successRate * 100,
                connectionUtilization, kafkaSuccessfulPublishes, kafkaFailedPublishes,
                kafkaRetryAttempts
            );
        }
    }
}