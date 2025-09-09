package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors dead letter queue metrics and provides alerting capabilities.
 * Tracks DLQ message accumulation and provides metrics for monitoring systems.
 */
@Component
public class DeadLetterQueueMonitor {
    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueueMonitor.class);
    
    private final AsyncKafkaPublisher asyncKafkaPublisher;
    private final BatchExecutionProperties batchProperties;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter dlqMessagesCounter;
    private final AtomicLong currentDlqMessageCount = new AtomicLong(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);
    
    // Alert thresholds
    private static final long DLQ_ALERT_THRESHOLD = 10; // Alert when DLQ has 10+ messages
    private static final long ALERT_COOLDOWN_MS = 300000; // 5 minutes between alerts
    
    public DeadLetterQueueMonitor(
            AsyncKafkaPublisher asyncKafkaPublisher,
            BatchExecutionProperties batchProperties,
            MeterRegistry meterRegistry) {
        this.asyncKafkaPublisher = asyncKafkaPublisher;
        this.batchProperties = batchProperties;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.dlqMessagesCounter = Counter.builder("kafka.dlq.messages.total")
                .description("Total number of messages sent to dead letter queue")
                .register(meterRegistry);
                
        Gauge.builder("kafka.dlq.messages.current", this, DeadLetterQueueMonitor::getCurrentDlqMessageCount)
                .description("Current number of messages in dead letter queue")
                .register(meterRegistry);
    }
    
    /**
     * Scheduled method to monitor DLQ metrics and trigger alerts.
     * Runs every minute to check for DLQ accumulation.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorDeadLetterQueue() {
        if (!batchProperties.getKafka().isEnableDeadLetterQueue()) {
            return; // Skip monitoring if DLQ is disabled
        }
        
        try {
            AsyncKafkaPublisher.PublishMetrics metrics = asyncKafkaPublisher.getMetrics();
            long dlqMessages = metrics.getDeadLetterMessages();
            
            // Update current count (this would ideally come from Kafka consumer lag monitoring)
            currentDlqMessageCount.set(dlqMessages);
            
            // Update counter metric
            double previousCount = dlqMessagesCounter.count();
            if (dlqMessages > previousCount) {
                dlqMessagesCounter.increment(dlqMessages - previousCount);
            }
            
            // Check for alert conditions
            checkAlertConditions(dlqMessages, metrics);
            
            logger.debug("DLQ monitoring: {} total messages sent to DLQ, circuit breaker state: {}", 
                dlqMessages, metrics.getCircuitState());
                
        } catch (Exception e) {
            logger.error("Error during DLQ monitoring: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Checks if alert conditions are met and triggers alerts if necessary.
     */
    private void checkAlertConditions(long dlqMessages, AsyncKafkaPublisher.PublishMetrics metrics) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAlert = currentTime - lastAlertTime.get();
        
        // Check if we should alert based on DLQ accumulation
        if (dlqMessages >= DLQ_ALERT_THRESHOLD && timeSinceLastAlert >= ALERT_COOLDOWN_MS) {
            triggerDlqAccumulationAlert(dlqMessages, metrics);
            lastAlertTime.set(currentTime);
        }
        
        // Check if circuit breaker is open (indicates persistent issues)
        if (metrics.getCircuitState() == AsyncKafkaPublisher.CircuitBreakerState.OPEN && 
            timeSinceLastAlert >= ALERT_COOLDOWN_MS) {
            triggerCircuitBreakerAlert(metrics);
            lastAlertTime.set(currentTime);
        }
    }
    
    /**
     * Triggers an alert for DLQ message accumulation.
     */
    private void triggerDlqAccumulationAlert(long dlqMessages, AsyncKafkaPublisher.PublishMetrics metrics) {
        String alertMessage = String.format(
            "ALERT: Dead Letter Queue accumulation detected. " +
            "DLQ messages: %d, Success rate: %.2f%%, Circuit breaker: %s, " +
            "Failed publishes: %d, Retry attempts: %d",
            dlqMessages,
            metrics.getSuccessRate() * 100,
            metrics.getCircuitState(),
            metrics.getFailedPublishes(),
            metrics.getRetriedPublishes()
        );
        
        logger.error(alertMessage);
        
        // In a real implementation, this would integrate with alerting systems like:
        // - PagerDuty
        // - Slack notifications
        // - Email alerts
        // - Prometheus AlertManager
        
        // For now, we'll use structured logging that can be picked up by log aggregation systems
        logger.error("DLQ_ACCUMULATION_ALERT: dlq_messages={}, success_rate={}, circuit_state={}, " +
                    "failed_publishes={}, retry_attempts={}, timestamp={}", 
                    dlqMessages, metrics.getSuccessRate(), metrics.getCircuitState(),
                    metrics.getFailedPublishes(), metrics.getRetriedPublishes(), OffsetDateTime.now());
    }
    
    /**
     * Triggers an alert for circuit breaker being open.
     */
    private void triggerCircuitBreakerAlert(AsyncKafkaPublisher.PublishMetrics metrics) {
        String alertMessage = String.format(
            "ALERT: Kafka circuit breaker is OPEN. " +
            "Failure count: %d, Total attempts: %d, Success rate: %.2f%%",
            metrics.getCurrentFailureCount(),
            metrics.getTotalAttempts(),
            metrics.getSuccessRate() * 100
        );
        
        logger.error(alertMessage);
        
        // Structured logging for alerting systems
        logger.error("CIRCUIT_BREAKER_OPEN_ALERT: failure_count={}, total_attempts={}, success_rate={}, " +
                    "circuit_state={}, timestamp={}", 
                    metrics.getCurrentFailureCount(), metrics.getTotalAttempts(), 
                    metrics.getSuccessRate(), metrics.getCircuitState(), OffsetDateTime.now());
    }
    
    /**
     * Gets the current DLQ message count for metrics.
     */
    public double getCurrentDlqMessageCount() {
        return currentDlqMessageCount.get();
    }
    
    /**
     * Gets DLQ monitoring statistics.
     */
    public DlqMonitoringStats getMonitoringStats() {
        AsyncKafkaPublisher.PublishMetrics publishMetrics = asyncKafkaPublisher.getMetrics();
        
        return new DlqMonitoringStats(
            publishMetrics.getDeadLetterMessages(),
            currentDlqMessageCount.get(),
            publishMetrics.getCircuitState(),
            publishMetrics.getSuccessRate(),
            lastAlertTime.get()
        );
    }
    
    /**
     * Manually triggers a DLQ check (for testing or admin purposes).
     */
    public void triggerManualCheck() {
        logger.info("Manual DLQ monitoring check triggered");
        monitorDeadLetterQueue();
    }
    
    /**
     * Resets alert cooldown (for testing purposes).
     */
    public void resetAlertCooldown() {
        lastAlertTime.set(0);
        logger.info("DLQ alert cooldown reset");
    }
    
    /**
     * DLQ monitoring statistics.
     */
    public static class DlqMonitoringStats {
        private final long totalDlqMessages;
        private final long currentDlqMessages;
        private final AsyncKafkaPublisher.CircuitBreakerState circuitState;
        private final double successRate;
        private final long lastAlertTime;
        
        public DlqMonitoringStats(long totalDlqMessages, long currentDlqMessages, 
                                AsyncKafkaPublisher.CircuitBreakerState circuitState, 
                                double successRate, long lastAlertTime) {
            this.totalDlqMessages = totalDlqMessages;
            this.currentDlqMessages = currentDlqMessages;
            this.circuitState = circuitState;
            this.successRate = successRate;
            this.lastAlertTime = lastAlertTime;
        }
        
        // Getters
        public long getTotalDlqMessages() { return totalDlqMessages; }
        public long getCurrentDlqMessages() { return currentDlqMessages; }
        public AsyncKafkaPublisher.CircuitBreakerState getCircuitState() { return circuitState; }
        public double getSuccessRate() { return successRate; }
        public long getLastAlertTime() { return lastAlertTime; }
        
        public boolean isHealthy() {
            return circuitState == AsyncKafkaPublisher.CircuitBreakerState.CLOSED && 
                   successRate > 0.95 && // 95% success rate threshold
                   totalDlqMessages < DLQ_ALERT_THRESHOLD;
        }
    }
}