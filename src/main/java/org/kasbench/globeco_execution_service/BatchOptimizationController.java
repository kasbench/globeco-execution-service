package org.kasbench.globeco_execution_service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for batch size optimization and performance benchmarking.
 * Provides endpoints for monitoring and tuning batch processing performance.
 */
@RestController
@RequestMapping("/api/batch-optimization")
@Tag(name = "Batch Optimization", description = "Batch size optimization and performance benchmarking")
public class BatchOptimizationController {

    private static final Logger logger = LoggerFactory.getLogger(BatchOptimizationController.class);

    private final BatchSizeOptimizer batchSizeOptimizer;
    private final BatchPerformanceBenchmark performanceBenchmark;
    private final BatchExecutionProperties batchExecutionProperties;

    @Autowired
    public BatchOptimizationController(BatchSizeOptimizer batchSizeOptimizer,
                                     BatchPerformanceBenchmark performanceBenchmark,
                                     BatchExecutionProperties batchExecutionProperties) {
        this.batchSizeOptimizer = batchSizeOptimizer;
        this.performanceBenchmark = performanceBenchmark;
        this.batchExecutionProperties = batchExecutionProperties;
    }

    /**
     * Get current batch size optimization statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get batch size optimization statistics",
              description = "Returns current batch size optimization statistics and performance metrics")
    public ResponseEntity<BatchSizeOptimizer.BatchSizeStats> getBatchSizeStats() {
        try {
            BatchSizeOptimizer.BatchSizeStats stats = batchSizeOptimizer.getBatchSizeStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error retrieving batch size stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get optimal batch size for a given request size.
     */
    @GetMapping("/optimal-size")
    @Operation(summary = "Get optimal batch size",
              description = "Returns the optimal batch size for a given request size based on current system performance")
    public ResponseEntity<OptimalBatchSizeResponse> getOptimalBatchSize(
            @Parameter(description = "Requested batch size", example = "1000")
            @RequestParam(defaultValue = "500") int requestedSize) {
        try {
            int optimalSize = batchSizeOptimizer.getOptimalBatchSize(requestedSize);
            int[] splits = batchSizeOptimizer.calculateBatchSplits(requestedSize);
            
            OptimalBatchSizeResponse response = new OptimalBatchSizeResponse(
                    requestedSize, optimalSize, splits,
                    batchExecutionProperties.getPerformance().isEnableDynamicBatchSizing());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error calculating optimal batch size", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Run a performance benchmark across different batch sizes.
     */
    @PostMapping("/benchmark")
    @Operation(summary = "Run batch performance benchmark",
              description = "Runs a comprehensive performance benchmark across different batch sizes")
    public ResponseEntity<BatchPerformanceBenchmark.BenchmarkResults> runBenchmark(
            @RequestBody BenchmarkRequest request) {
        try {
            logger.info("Starting batch performance benchmark: {}", request);
            
            BatchPerformanceBenchmark.BenchmarkConfiguration config = 
                    new BatchPerformanceBenchmark.BenchmarkConfiguration(
                            request.getBatchSizes(), request.getIterationsPerBatchSize());
            
            BatchPerformanceBenchmark.BenchmarkResults results = performanceBenchmark.runBenchmark(config);
            
            logger.info("Benchmark completed: {}", results.getSummary());
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            logger.error("Error running batch benchmark", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Run a concurrent performance benchmark.
     */
    @PostMapping("/benchmark/concurrent")
    @Operation(summary = "Run concurrent batch performance benchmark",
              description = "Runs a concurrent performance benchmark to test system behavior under load")
    public ResponseEntity<BatchPerformanceBenchmark.ConcurrentBenchmarkResults> runConcurrentBenchmark(
            @RequestBody ConcurrentBenchmarkRequest request) {
        try {
            logger.info("Starting concurrent batch performance benchmark: {}", request);
            
            BatchPerformanceBenchmark.ConcurrentBenchmarkConfiguration config = 
                    new BatchPerformanceBenchmark.ConcurrentBenchmarkConfiguration(
                            request.getConcurrentThreads(), request.getBatchesPerThread(),
                            request.getBatchSize(), request.getTimeoutMinutes());
            
            BatchPerformanceBenchmark.ConcurrentBenchmarkResults results = 
                    performanceBenchmark.runConcurrentBenchmark(config);
            
            logger.info("Concurrent benchmark completed: overall throughput={:.1f} exec/sec, success rate={:.1f}%",
                       results.getOverallThroughput(), results.getOverallSuccessRate() * 100);
            
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            logger.error("Error running concurrent batch benchmark", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Reset batch size optimization statistics.
     */
    @PostMapping("/reset-stats")
    @Operation(summary = "Reset optimization statistics",
              description = "Resets batch size optimization statistics and performance counters")
    public ResponseEntity<String> resetOptimizationStats() {
        try {
            batchSizeOptimizer.resetStats();
            logger.info("Batch size optimization statistics reset");
            return ResponseEntity.ok("Batch size optimization statistics reset successfully");
        } catch (Exception e) {
            logger.error("Error resetting optimization stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get current batch execution configuration.
     */
    @GetMapping("/configuration")
    @Operation(summary = "Get batch execution configuration",
              description = "Returns current batch execution configuration settings")
    public ResponseEntity<BatchConfigurationResponse> getBatchConfiguration() {
        try {
            BatchConfigurationResponse response = new BatchConfigurationResponse(
                    batchExecutionProperties.getBulkInsertBatchSize(),
                    batchExecutionProperties.getPerformance().getMinBatchSize(),
                    batchExecutionProperties.getPerformance().getMaxBatchSize(),
                    batchExecutionProperties.getPerformance().isEnableDynamicBatchSizing(),
                    batchExecutionProperties.getMaxConcurrentBatches());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving batch configuration", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request DTO for batch benchmarking.
     */
    public static class BenchmarkRequest {
        private int[] batchSizes = {50, 100, 250, 500, 1000};
        private int iterationsPerBatchSize = 3;

        public int[] getBatchSizes() { return batchSizes; }
        public void setBatchSizes(int[] batchSizes) { this.batchSizes = batchSizes; }
        public int getIterationsPerBatchSize() { return iterationsPerBatchSize; }
        public void setIterationsPerBatchSize(int iterationsPerBatchSize) { this.iterationsPerBatchSize = iterationsPerBatchSize; }

        @Override
        public String toString() {
            return String.format("BenchmarkRequest{batchSizes=%s, iterations=%d}", 
                               java.util.Arrays.toString(batchSizes), iterationsPerBatchSize);
        }
    }

    /**
     * Request DTO for concurrent benchmarking.
     */
    public static class ConcurrentBenchmarkRequest {
        private int concurrentThreads = 5;
        private int batchesPerThread = 3;
        private int batchSize = 500;
        private int timeoutMinutes = 5;

        public int getConcurrentThreads() { return concurrentThreads; }
        public void setConcurrentThreads(int concurrentThreads) { this.concurrentThreads = concurrentThreads; }
        public int getBatchesPerThread() { return batchesPerThread; }
        public void setBatchesPerThread(int batchesPerThread) { this.batchesPerThread = batchesPerThread; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(int timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }

        @Override
        public String toString() {
            return String.format("ConcurrentBenchmarkRequest{threads=%d, batches=%d, size=%d, timeout=%d}", 
                               concurrentThreads, batchesPerThread, batchSize, timeoutMinutes);
        }
    }

    /**
     * Response DTO for optimal batch size calculation.
     */
    public static class OptimalBatchSizeResponse {
        private final int requestedSize;
        private final int optimalSize;
        private final int[] batchSplits;
        private final boolean dynamicSizingEnabled;

        public OptimalBatchSizeResponse(int requestedSize, int optimalSize, int[] batchSplits, boolean dynamicSizingEnabled) {
            this.requestedSize = requestedSize;
            this.optimalSize = optimalSize;
            this.batchSplits = batchSplits;
            this.dynamicSizingEnabled = dynamicSizingEnabled;
        }

        public int getRequestedSize() { return requestedSize; }
        public int getOptimalSize() { return optimalSize; }
        public int[] getBatchSplits() { return batchSplits; }
        public boolean isDynamicSizingEnabled() { return dynamicSizingEnabled; }
    }

    /**
     * Response DTO for batch configuration.
     */
    public static class BatchConfigurationResponse {
        private final int baseBatchSize;
        private final int minBatchSize;
        private final int maxBatchSize;
        private final boolean dynamicSizingEnabled;
        private final int maxConcurrentBatches;

        public BatchConfigurationResponse(int baseBatchSize, int minBatchSize, int maxBatchSize, 
                                        boolean dynamicSizingEnabled, int maxConcurrentBatches) {
            this.baseBatchSize = baseBatchSize;
            this.minBatchSize = minBatchSize;
            this.maxBatchSize = maxBatchSize;
            this.dynamicSizingEnabled = dynamicSizingEnabled;
            this.maxConcurrentBatches = maxConcurrentBatches;
        }

        public int getBaseBatchSize() { return baseBatchSize; }
        public int getMinBatchSize() { return minBatchSize; }
        public int getMaxBatchSize() { return maxBatchSize; }
        public boolean isDynamicSizingEnabled() { return dynamicSizingEnabled; }
        public int getMaxConcurrentBatches() { return maxConcurrentBatches; }
    }
}