package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BatchProcessingMetrics to verify comprehensive metrics collection
 * and accuracy across all batch processing operations.
 */
class BatchProcessingMetricsIntegrationTest {
    
    private MeterRegistry meterRegistry;
    private BatchProcessingMetrics metrics;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new BatchProcessingMetrics(meterRegistry);
    }
    
    @Test
    void testBatchProcessingMetricsCollection() {
        // Test batch processing metrics
        int batchSize = 100;
        io.micrometer.core.instrument.Timer.Sample sample = metrics.startBatchProcessing(batchSize);
        
        // Simulate some processing time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        metrics.recordBatchProcessingComplete(sample, true, batchSize, 95);
        
        // Verify batch processing metrics
        Counter batchRequestsCounter = meterRegistry.get("batch.requests.total").counter();
        Counter batchSuccessCounter = meterRegistry.get("batch.requests.success").counter();
        Counter executionProcessedCounter = meterRegistry.get("batch.executions.processed").counter();
        Counter executionSuccessCounter = meterRegistry.get("batch.executions.success").counter();
        Timer batchProcessingTimer = meterRegistry.get("batch.processing.duration").timer();
        
        assertThat(batchRequestsCounter.count()).isEqualTo(1.0);
        assertThat(batchSuccessCounter.count()).isEqualTo(1.0);
        assertThat(executionProcessedCounter.count()).isEqualTo(100.0);
        assertThat(executionSuccessCounter.count()).isEqualTo(95.0);
        assertThat(batchProcessingTimer.count()).isEqualTo(1);
        assertThat(batchProcessingTimer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(40);
    }
    
    @Test
    void testDatabaseOperationMetrics() {
        // Test bulk insert metrics
        Duration insertDuration = Duration.ofMillis(150);
        metrics.recordBulkInsert(500, insertDuration);
        
        // Test bulk update metrics
        Duration updateDuration = Duration.ofMillis(75);
        metrics.recordBulkUpdate(250, updateDuration);
        
        // Test database error metrics
        metrics.recordDatabaseError("bulk_insert", "ConstraintViolationException");
        metrics.recordDatabaseError("bulk_update", "TimeoutException");
        
        // Verify database operation metrics
        Timer bulkInsertTimer = meterRegistry.get("database.bulk.insert.duration").timer();
        Timer bulkUpdateTimer = meterRegistry.get("database.bulk.update.duration").timer();
        Counter databaseOperationCounter = meterRegistry.get("database.operations.total").counter();
        Counter databaseErrorCounter = meterRegistry.get("database.operations.error").counter();
        
        assertThat(bulkInsertTimer.count()).isEqualTo(1);
        assertThat(bulkInsertTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150);
        
        assertThat(bulkUpdateTimer.count()).isEqualTo(1);
        assertThat(bulkUpdateTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(75);
        
        assertThat(databaseOperationCounter.count()).isEqualTo(2.0);
        assertThat(databaseErrorCounter.count()).isEqualTo(2.0);
    }
    
    @Test
    void testKafkaPublishingMetrics() {
        // Test successful Kafka publish
        Duration successDuration = Duration.ofMillis(25);
        metrics.recordKafkaPublishSuccess(successDuration);
        metrics.recordKafkaPublishSuccess(Duration.ofMillis(30));
        
        // Test failed Kafka publish
        Duration failureDuration = Duration.ofMillis(100);
        metrics.recordKafkaPublishFailure(failureDuration, "TimeoutException");
        
        // Test retry attempts
        metrics.recordKafkaRetry(1);
        metrics.recordKafkaRetry(2);
        metrics.recordKafkaRetry(3);
        
        // Test circuit breaker
        metrics.recordKafkaCircuitBreakerOpen("failure_threshold_exceeded");
        
        // Verify Kafka publishing metrics
        Timer kafkaPublishTimer = meterRegistry.get("kafka.publish.duration").timer();
        Counter kafkaSuccessCounter = meterRegistry.get("kafka.publish.success").counter();
        Counter kafkaFailureCounter = meterRegistry.get("kafka.publish.failure").counter();
        Counter kafkaRetryCounter = meterRegistry.get("kafka.publish.retry").counter();
        Counter kafkaCircuitBreakerCounter = meterRegistry.get("kafka.circuit.breaker.open").counter();
        
        assertThat(kafkaPublishTimer.count()).isEqualTo(3); // 2 success + 1 failure
        assertThat(kafkaSuccessCounter.count()).isEqualTo(2.0);
        assertThat(kafkaFailureCounter.count()).isEqualTo(1.0);
        assertThat(kafkaRetryCounter.count()).isEqualTo(3.0);
        assertThat(kafkaCircuitBreakerCounter.count()).isEqualTo(1.0);
    }
    
    @Test
    void testConnectionPoolMetrics() {
        // Test connection pool metrics updates
        metrics.updateConnectionPoolMetrics(15, 20, 2);
        
        // Verify connection pool gauges
        Gauge activeConnectionsGauge = meterRegistry.get("database.connection.pool.active").gauge();
        Gauge maxConnectionsGauge = meterRegistry.get("database.connection.pool.max").gauge();
        Gauge utilizationGauge = meterRegistry.get("database.connection.pool.utilization").gauge();
        
        assertThat(activeConnectionsGauge.value()).isEqualTo(15.0);
        assertThat(maxConnectionsGauge.value()).isEqualTo(20.0);
        assertThat(utilizationGauge.value()).isEqualTo(75.0); // 15/20 * 100
    }
    
    @Test
    void testPerformanceCalculationMetrics() {
        // Simulate multiple batch operations to test performance calculations
        for (int i = 0; i < 5; i++) {
            io.micrometer.core.instrument.Timer.Sample sample = metrics.startBatchProcessing(50);
            try {
                Thread.sleep(20); // Simulate processing time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            metrics.recordBatchProcessingComplete(sample, true, 50, 48);
        }
        
        // Verify performance gauges
        Gauge throughputGauge = meterRegistry.get("batch.processing.throughput").gauge();
        Gauge avgDurationGauge = meterRegistry.get("batch.processing.average.duration").gauge();
        Gauge successRateGauge = meterRegistry.get("batch.processing.success.rate").gauge();
        
        assertThat(throughputGauge.value()).isGreaterThan(0.0);
        assertThat(avgDurationGauge.value()).isGreaterThan(0.0);
        assertThat(successRateGauge.value()).isEqualTo(1.0); // 100% success rate
    }
    
    @Test
    void testMetricsSummary() {
        // Create a comprehensive scenario
        io.micrometer.core.instrument.Timer.Sample sample1 = metrics.startBatchProcessing(100);
        metrics.recordBatchProcessingComplete(sample1, true, 100, 95);
        
        io.micrometer.core.instrument.Timer.Sample sample2 = metrics.startBatchProcessing(50);
        metrics.recordBatchProcessingComplete(sample2, false, 50, 30);
        
        metrics.recordBulkInsert(100, Duration.ofMillis(200));
        metrics.recordBulkUpdate(50, Duration.ofMillis(100));
        
        metrics.recordKafkaPublishSuccess(Duration.ofMillis(25));
        metrics.recordKafkaPublishFailure(Duration.ofMillis(50), "TimeoutException");
        metrics.recordKafkaRetry(1);
        
        metrics.updateConnectionPoolMetrics(12, 20, 0);
        
        // Get metrics summary
        BatchProcessingMetrics.MetricsSummary summary = metrics.getMetricsSummary();
        
        // Verify summary data
        assertThat(summary.getTotalBatchRequests()).isEqualTo(2.0);
        assertThat(summary.getSuccessfulBatchRequests()).isEqualTo(1.0);
        assertThat(summary.getFailedBatchRequests()).isEqualTo(1.0);
        assertThat(summary.getTotalExecutionsProcessed()).isEqualTo(150.0);
        assertThat(summary.getSuccessfulExecutions()).isEqualTo(125.0);
        assertThat(summary.getFailedExecutions()).isEqualTo(25.0);
        assertThat(summary.getConnectionUtilization()).isEqualTo(60.0); // 12/20 * 100
        assertThat(summary.getKafkaSuccessfulPublishes()).isEqualTo(1.0);
        assertThat(summary.getKafkaFailedPublishes()).isEqualTo(1.0);
        assertThat(summary.getKafkaRetryAttempts()).isEqualTo(1.0);
        
        // Test health check
        assertThat(summary.isHealthy()).isFalse(); // Should be false due to 50% batch success rate
    }
    
    @Test
    void testMetricsAccuracyUnderLoad() {
        // Simulate high-load scenario with concurrent operations
        int iterations = 20;
        int batchSize = 25;
        
        for (int i = 0; i < iterations; i++) {
            io.micrometer.core.instrument.Timer.Sample sample = metrics.startBatchProcessing(batchSize);
            
            // Simulate processing
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean successful = i % 4 != 0; // 75% success rate
            int successCount = successful ? batchSize : batchSize / 2;
            
            metrics.recordBatchProcessingComplete(sample, successful, batchSize, successCount);
            
            // Add database operations
            metrics.recordBulkInsert(batchSize, Duration.ofMillis(50 + i));
            
            // Add Kafka operations
            if (successful) {
                metrics.recordKafkaPublishSuccess(Duration.ofMillis(20 + i));
            } else {
                metrics.recordKafkaPublishFailure(Duration.ofMillis(30 + i), "NetworkException");
                metrics.recordKafkaRetry(1);
            }
        }
        
        // Verify final metrics accuracy
        Counter batchRequestsCounter = meterRegistry.get("batch.requests.total").counter();
        Counter batchSuccessCounter = meterRegistry.get("batch.requests.success").counter();
        Counter executionProcessedCounter = meterRegistry.get("batch.executions.processed").counter();
        Timer bulkInsertTimer = meterRegistry.get("database.bulk.insert.duration").timer();
        Counter kafkaSuccessCounter = meterRegistry.get("kafka.publish.success").counter();
        Counter kafkaFailureCounter = meterRegistry.get("kafka.publish.failure").counter();
        
        assertThat(batchRequestsCounter.count()).isEqualTo(iterations);
        assertThat(batchSuccessCounter.count()).isEqualTo(15.0); // 75% of 20
        assertThat(executionProcessedCounter.count()).isEqualTo(iterations * batchSize);
        assertThat(bulkInsertTimer.count()).isEqualTo(iterations);
        assertThat(kafkaSuccessCounter.count()).isEqualTo(15.0);
        assertThat(kafkaFailureCounter.count()).isEqualTo(5.0);
        
        // Verify performance metrics are reasonable
        BatchProcessingMetrics.MetricsSummary summary = metrics.getMetricsSummary();
        assertThat(summary.getThroughput()).isGreaterThan(0.0);
        assertThat(summary.getSuccessRate()).isEqualTo(0.75); // 75% success rate
    }
    
    @Test
    void testMetricsReset() {
        // Add some metrics
        io.micrometer.core.instrument.Timer.Sample sample = metrics.startBatchProcessing(50);
        metrics.recordBatchProcessingComplete(sample, true, 50, 50);
        metrics.updateConnectionPoolMetrics(10, 20, 0);
        
        // Verify metrics exist
        assertThat(metrics.getMetricsSummary().getTotalBatchRequests()).isEqualTo(1.0);
        assertThat(metrics.getConnectionUtilization()).isEqualTo(50.0);
        
        // Reset metrics
        metrics.resetMetrics();
        
        // Verify internal state is reset (note: Micrometer counters/timers cannot be reset)
        assertThat(metrics.getThroughput()).isEqualTo(0.0);
        assertThat(metrics.getAverageProcessingTime()).isEqualTo(0.0);
        assertThat(metrics.getConnectionUtilization()).isEqualTo(0.0);
    }
    
    @Test
    void testErrorScenarioMetrics() {
        // Test various error scenarios
        metrics.recordDatabaseError("bulk_insert", "ConstraintViolationException");
        metrics.recordDatabaseError("bulk_insert", "TimeoutException");
        metrics.recordDatabaseError("bulk_update", "DeadlockException");
        
        metrics.recordKafkaPublishFailure(Duration.ofMillis(100), "BrokerNotAvailableException");
        metrics.recordKafkaPublishFailure(Duration.ofMillis(150), "TimeoutException");
        
        metrics.recordKafkaCircuitBreakerOpen("consecutive_failures");
        metrics.recordKafkaCircuitBreakerOpen("broker_unavailable");
        
        // Verify error metrics
        Counter databaseErrorCounter = meterRegistry.get("database.operations.error").counter();
        Counter kafkaFailureCounter = meterRegistry.get("kafka.publish.failure").counter();
        Counter circuitBreakerCounter = meterRegistry.get("kafka.circuit.breaker.open").counter();
        
        assertThat(databaseErrorCounter.count()).isEqualTo(3.0);
        assertThat(kafkaFailureCounter.count()).isEqualTo(2.0);
        assertThat(circuitBreakerCounter.count()).isEqualTo(2.0);
    }
}