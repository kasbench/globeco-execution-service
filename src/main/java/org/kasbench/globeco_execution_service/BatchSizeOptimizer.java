package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dynamically optimizes batch sizes based on system performance metrics.
 * Adjusts batch sizes to prevent resource exhaustion and maintain optimal throughput.
 */
@Component
public class BatchSizeOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(BatchSizeOptimizer.class);

    private final BatchExecutionProperties batchExecutionProperties;
    private final ConnectionPoolMonitor connectionPoolMonitor;
    private final MeterRegistry meterRegistry;
    
    // Performance tracking
    private final AtomicInteger currentOptimalBatchSize;
    private final AtomicLong lastOptimizationTime = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger totalBatchesProcessed = new AtomicInteger(0);
    
    // Performance thresholds
    private static final double HIGH_CONNECTION_UTILIZATION_THRESHOLD = 0.8;
    private static final double CRITICAL_CONNECTION_UTILIZATION_THRESHOLD = 0.95;
    private static final long SLOW_PROCESSING_THRESHOLD_MS = 10000; // 10 seconds
    private static final long OPTIMIZATION_INTERVAL_MS = 30000; // 30 seconds
    
    // Batch size adjustment factors
    private static final double DECREASE_FACTOR = 0.8;
    private static final double INCREASE_FACTOR = 1.2;
    private static final double AGGRESSIVE_DECREASE_FACTOR = 0.6;
    
    @Autowired
    public BatchSizeOptimizer(BatchExecutionProperties batchExecutionProperties,
                            ConnectionPoolMonitor connectionPoolMonitor,
                            MeterRegistry meterRegistry) {
        this.batchExecutionProperties = batchExecutionProperties;
        this.connectionPoolMonitor = connectionPoolMonitor;
        this.meterRegistry = meterRegistry;
        
        // Initialize with configured batch size
        this.currentOptimalBatchSize = new AtomicInteger(batchExecutionProperties.getBulkInsertBatchSize());
        
        registerMetrics();
        
        logger.info("BatchSizeOptimizer initialized with base batch size: {}, dynamic optimization: {}",
                   batchExecutionProperties.getBulkInsertBatchSize(),
                   batchExecutionProperties.getPerformance().isEnableDynamicBatchSizing());
    }
    
    /**
     * Gets the optimal batch size based on current system performance.
     * If dynamic sizing is disabled, returns the configured batch size.
     */
    public int getOptimalBatchSize(int requestedBatchSize) {
        if (!batchExecutionProperties.getPerformance().isEnableDynamicBatchSizing()) {
            return Math.min(requestedBatchSize, batchExecutionProperties.getBulkInsertBatchSize());
        }
        
        int optimalSize = currentOptimalBatchSize.get();
        int adjustedSize = Math.min(requestedBatchSize, optimalSize);
        
        logger.debug("Optimal batch size calculation: requested={}, optimal={}, adjusted={}",
                    requestedBatchSize, optimalSize, adjustedSize);
        
        return adjustedSize;
    }
    
    /**
     * Records batch processing performance and adjusts optimal batch size if needed.
     */
    public void recordBatchPerformance(int batchSize, long processingTimeMs, boolean success) {
        totalBatchesProcessed.incrementAndGet();
        totalProcessingTime.addAndGet(processingTimeMs);
        
        if (!batchExecutionProperties.getPerformance().isEnableDynamicBatchSizing()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastOptimization = currentTime - lastOptimizationTime.get();
        
        // Only optimize periodically to avoid thrashing
        if (timeSinceLastOptimization < OPTIMIZATION_INTERVAL_MS) {
            return;
        }
        
        if (lastOptimizationTime.compareAndSet(currentTime - timeSinceLastOptimization, currentTime)) {
            optimizeBatchSize(batchSize, processingTimeMs, success);
        }
    }
    
    /**
     * Optimizes batch size based on current performance metrics.
     */
    private void optimizeBatchSize(int lastBatchSize, long lastProcessingTime, boolean lastSuccess) {
        try {
            ConnectionPoolMonitor.ConnectionPoolStats poolStats = connectionPoolMonitor.getConnectionPoolStats();
            double connectionUtilization = poolStats.getUtilization();
            int threadsAwaiting = poolStats.getThreadsAwaiting();
            
            int currentOptimal = currentOptimalBatchSize.get();
            int newOptimal = currentOptimal;
            String reason = "no change";
            
            // Determine if we should decrease batch size
            if (!lastSuccess) {
                newOptimal = (int) (currentOptimal * AGGRESSIVE_DECREASE_FACTOR);
                reason = "batch processing failed";
            } else if (connectionUtilization >= CRITICAL_CONNECTION_UTILIZATION_THRESHOLD) {
                newOptimal = (int) (currentOptimal * AGGRESSIVE_DECREASE_FACTOR);
                reason = "critical connection pool utilization: " + String.format("%.1f%%", connectionUtilization * 100);
            } else if (connectionUtilization >= HIGH_CONNECTION_UTILIZATION_THRESHOLD || threadsAwaiting > 5) {
                newOptimal = (int) (currentOptimal * DECREASE_FACTOR);
                reason = "high connection pool utilization: " + String.format("%.1f%%", connectionUtilization * 100) + 
                        ", threads awaiting: " + threadsAwaiting;
            } else if (lastProcessingTime > SLOW_PROCESSING_THRESHOLD_MS) {
                newOptimal = (int) (currentOptimal * DECREASE_FACTOR);
                reason = "slow processing time: " + lastProcessingTime + "ms";
            }
            // Determine if we should increase batch size
            else if (connectionUtilization < 0.5 && threadsAwaiting == 0 && lastProcessingTime < 5000) {
                newOptimal = (int) (currentOptimal * INCREASE_FACTOR);
                reason = "low resource utilization, good performance";
            }
            
            // Apply constraints
            BatchExecutionProperties.PerformanceProperties perfProps = batchExecutionProperties.getPerformance();
            newOptimal = Math.max(newOptimal, perfProps.getMinBatchSize());
            newOptimal = Math.min(newOptimal, perfProps.getMaxBatchSize());
            
            // Update if changed
            if (newOptimal != currentOptimal) {
                currentOptimalBatchSize.set(newOptimal);
                logger.info("Batch size optimized: {} -> {} (reason: {})", 
                           currentOptimal, newOptimal, reason);
                
                // Record the optimization event
                meterRegistry.counter("batch.size.optimization.events",
                                    "direction", newOptimal > currentOptimal ? "increase" : "decrease",
                                    "reason", reason.split(":")[0])
                            .increment();
            }
            
        } catch (Exception e) {
            logger.error("Error during batch size optimization", e);
        }
    }
    
    /**
     * Splits a large batch into smaller chunks based on optimal batch size.
     */
    public int[] calculateBatchSplits(int totalSize) {
        int optimalSize = getOptimalBatchSize(totalSize);
        
        if (totalSize <= optimalSize) {
            return new int[]{totalSize};
        }
        
        int numberOfBatches = (int) Math.ceil((double) totalSize / optimalSize);
        int[] batchSizes = new int[numberOfBatches];
        
        int remaining = totalSize;
        for (int i = 0; i < numberOfBatches; i++) {
            batchSizes[i] = Math.min(optimalSize, remaining);
            remaining -= batchSizes[i];
        }
        
        logger.debug("Split batch of {} into {} chunks: optimal size={}, splits={}",
                    totalSize, numberOfBatches, optimalSize, batchSizes);
        
        return batchSizes;
    }
    
    /**
     * Gets current performance statistics for monitoring.
     */
    public BatchSizeStats getBatchSizeStats() {
        int totalBatches = totalBatchesProcessed.get();
        long totalTime = totalProcessingTime.get();
        double averageProcessingTime = totalBatches > 0 ? (double) totalTime / totalBatches : 0.0;
        
        return new BatchSizeStats(
                currentOptimalBatchSize.get(),
                batchExecutionProperties.getBulkInsertBatchSize(),
                batchExecutionProperties.getPerformance().getMinBatchSize(),
                batchExecutionProperties.getPerformance().getMaxBatchSize(),
                totalBatches,
                averageProcessingTime,
                batchExecutionProperties.getPerformance().isEnableDynamicBatchSizing()
        );
    }
    
    /**
     * Registers metrics for batch size optimization monitoring.
     */
    private void registerMetrics() {
        // Current optimal batch size gauge
        meterRegistry.gauge("batch.size.optimal.current", this, 
                          optimizer -> (double) optimizer.currentOptimalBatchSize.get());
        
        // Average processing time gauge
        meterRegistry.gauge("batch.processing.time.average", this, optimizer -> {
            int totalBatches = optimizer.totalBatchesProcessed.get();
            long totalTime = optimizer.totalProcessingTime.get();
            return totalBatches > 0 ? (double) totalTime / totalBatches : 0.0;
        });
        
        // Total batches processed gauge
        meterRegistry.gauge("batch.processing.total.count", this,
                          optimizer -> (double) optimizer.totalBatchesProcessed.get());
    }
    
    /**
     * Resets optimization statistics (useful for testing).
     */
    public void resetStats() {
        totalBatchesProcessed.set(0);
        totalProcessingTime.set(0);
        lastOptimizationTime.set(0);
        currentOptimalBatchSize.set(batchExecutionProperties.getBulkInsertBatchSize());
        
        logger.info("Batch size optimizer statistics reset");
    }
    
    /**
     * Data class for batch size statistics.
     */
    public static class BatchSizeStats {
        private final int currentOptimalSize;
        private final int configuredBaseSize;
        private final int minSize;
        private final int maxSize;
        private final int totalBatchesProcessed;
        private final double averageProcessingTime;
        private final boolean dynamicSizingEnabled;
        
        public BatchSizeStats(int currentOptimalSize, int configuredBaseSize, int minSize, int maxSize,
                            int totalBatchesProcessed, double averageProcessingTime, boolean dynamicSizingEnabled) {
            this.currentOptimalSize = currentOptimalSize;
            this.configuredBaseSize = configuredBaseSize;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.totalBatchesProcessed = totalBatchesProcessed;
            this.averageProcessingTime = averageProcessingTime;
            this.dynamicSizingEnabled = dynamicSizingEnabled;
        }
        
        // Getters
        public int getCurrentOptimalSize() { return currentOptimalSize; }
        public int getConfiguredBaseSize() { return configuredBaseSize; }
        public int getMinSize() { return minSize; }
        public int getMaxSize() { return maxSize; }
        public int getTotalBatchesProcessed() { return totalBatchesProcessed; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public boolean isDynamicSizingEnabled() { return dynamicSizingEnabled; }
    }
}