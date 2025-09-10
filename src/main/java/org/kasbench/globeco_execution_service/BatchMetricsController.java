package org.kasbench.globeco_execution_service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for exposing batch processing metrics and monitoring information.
 * Provides endpoints for monitoring dashboards and health checks.
 */
@RestController
@RequestMapping("/api/metrics")
public class BatchMetricsController {
    
    private final BatchProcessingMetrics batchMetrics;
    private final DatabaseMetricsMonitor databaseMonitor;
    private final DeadLetterQueueMonitor dlqMonitor;
    
    public BatchMetricsController(BatchProcessingMetrics batchMetrics,
                                DatabaseMetricsMonitor databaseMonitor,
                                DeadLetterQueueMonitor dlqMonitor) {
        this.batchMetrics = batchMetrics;
        this.databaseMonitor = databaseMonitor;
        this.dlqMonitor = dlqMonitor;
    }
    
    /**
     * Get comprehensive batch processing metrics summary.
     */
    @GetMapping("/batch-processing")
    public ResponseEntity<BatchProcessingMetrics.MetricsSummary> getBatchProcessingMetrics() {
        BatchProcessingMetrics.MetricsSummary summary = batchMetrics.getMetricsSummary();
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get database connection pool metrics.
     */
    @GetMapping("/database/connection-pool")
    public ResponseEntity<DatabaseMetricsMonitor.ConnectionPoolStats> getConnectionPoolMetrics() {
        DatabaseMetricsMonitor.ConnectionPoolStats stats = databaseMonitor.getConnectionPoolStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get dead letter queue monitoring statistics.
     */
    @GetMapping("/kafka/dlq")
    public ResponseEntity<DeadLetterQueueMonitor.DlqMonitoringStats> getDlqMetrics() {
        DeadLetterQueueMonitor.DlqMonitoringStats stats = dlqMonitor.getMonitoringStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get overall system health based on all metrics.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // Batch processing health
        BatchProcessingMetrics.MetricsSummary batchSummary = batchMetrics.getMetricsSummary();
        boolean batchHealthy = batchSummary.isHealthy();
        health.put("batchProcessing", Map.of(
            "healthy", batchHealthy,
            "successRate", batchSummary.getSuccessRate(),
            "throughput", batchSummary.getThroughput(),
            "avgProcessingTime", batchSummary.getAverageProcessingTime()
        ));
        
        // Database health
        DatabaseMetricsMonitor.ConnectionPoolStats dbStats = databaseMonitor.getConnectionPoolStats();
        boolean dbHealthy = dbStats.isHealthy();
        health.put("database", Map.of(
            "healthy", dbHealthy,
            "connectionUtilization", dbStats.getUtilizationPercentage(),
            "activeConnections", dbStats.getActiveConnections(),
            "threadsAwaiting", dbStats.getThreadsAwaitingConnection()
        ));
        
        // Kafka health
        DeadLetterQueueMonitor.DlqMonitoringStats dlqStats = dlqMonitor.getMonitoringStats();
        boolean kafkaHealthy = dlqStats.isHealthy();
        health.put("kafka", Map.of(
            "healthy", kafkaHealthy,
            "successRate", dlqStats.getSuccessRate(),
            "circuitState", dlqStats.getCircuitState().toString(),
            "dlqMessages", dlqStats.getTotalDlqMessages()
        ));
        
        // Overall health
        boolean overallHealthy = batchHealthy && dbHealthy && kafkaHealthy;
        health.put("overall", Map.of(
            "healthy", overallHealthy,
            "status", overallHealthy ? "UP" : "DOWN"
        ));
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get performance metrics for monitoring dashboards.
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        Map<String, Object> performance = new HashMap<>();
        
        BatchProcessingMetrics.MetricsSummary summary = batchMetrics.getMetricsSummary();
        
        performance.put("throughput", summary.getThroughput());
        performance.put("averageProcessingTime", summary.getAverageProcessingTime());
        performance.put("successRate", summary.getSuccessRate());
        performance.put("totalBatches", summary.getTotalBatchRequests());
        performance.put("totalExecutions", summary.getTotalExecutionsProcessed());
        performance.put("kafkaSuccessRate", 
            summary.getKafkaSuccessfulPublishes() / 
            (summary.getKafkaSuccessfulPublishes() + summary.getKafkaFailedPublishes() + 0.001));
        
        return ResponseEntity.ok(performance);
    }
    
    /**
     * Trigger manual metrics collection (for testing/admin purposes).
     */
    @GetMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshMetrics() {
        try {
            databaseMonitor.triggerManualUpdate();
            dlqMonitor.triggerManualCheck();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Metrics refresh triggered successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to refresh metrics: " + e.getMessage()
            ));
        }
    }
}