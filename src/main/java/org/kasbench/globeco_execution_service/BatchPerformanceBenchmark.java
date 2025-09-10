package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Performance benchmarking component for testing different batch sizes.
 * Provides automated performance testing and optimization recommendations.
 */
@Component
public class BatchPerformanceBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(BatchPerformanceBenchmark.class);

    private final ExecutionRepository executionRepository;
    private final BulkExecutionProcessor bulkExecutionProcessor;
    private final BatchSizeOptimizer batchSizeOptimizer;
    private final MeterRegistry meterRegistry;
    private final BatchExecutionProperties batchExecutionProperties;

    @Autowired
    public BatchPerformanceBenchmark(ExecutionRepository executionRepository,
                                   BulkExecutionProcessor bulkExecutionProcessor,
                                   BatchSizeOptimizer batchSizeOptimizer,
                                   MeterRegistry meterRegistry,
                                   BatchExecutionProperties batchExecutionProperties) {
        this.executionRepository = executionRepository;
        this.bulkExecutionProcessor = bulkExecutionProcessor;
        this.batchSizeOptimizer = batchSizeOptimizer;
        this.meterRegistry = meterRegistry;
        this.batchExecutionProperties = batchExecutionProperties;
    }

    /**
     * Runs a comprehensive performance benchmark across different batch sizes.
     */
    public BenchmarkResults runBenchmark(BenchmarkConfiguration config) {
        logger.info("Starting batch performance benchmark with configuration: {}", config);
        
        List<BenchmarkResult> results = new ArrayList<>();
        
        for (int batchSize : config.getBatchSizes()) {
            logger.info("Benchmarking batch size: {}", batchSize);
            
            BenchmarkResult result = benchmarkBatchSize(batchSize, config);
            results.add(result);
            
            // Brief pause between tests to allow system to stabilize
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        BenchmarkResults benchmarkResults = new BenchmarkResults(results, config);
        logger.info("Benchmark completed. Results: {}", benchmarkResults.getSummary());
        
        return benchmarkResults;
    }
    
    /**
     * Benchmarks a specific batch size with multiple iterations.
     */
    private BenchmarkResult benchmarkBatchSize(int batchSize, BenchmarkConfiguration config) {
        List<Long> processingTimes = new ArrayList<>();
        List<Double> throughputValues = new ArrayList<>();
        int successfulRuns = 0;
        int failedRuns = 0;
        
        for (int iteration = 0; iteration < config.getIterationsPerBatchSize(); iteration++) {
            try {
                // Generate test data
                List<ExecutionPostDTO> testExecutions = generateTestExecutions(batchSize);
                
                // Measure processing time
                Timer.Sample sample = Timer.start(meterRegistry);
                long startTime = System.currentTimeMillis();
                
                // Process the batch
                BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(testExecutions);
                
                long endTime = System.currentTimeMillis();
                long processingTime = endTime - startTime;
                sample.stop(Timer.builder("benchmark.batch.processing.time")
                          .tag("batch.size", String.valueOf(batchSize))
                          .register(meterRegistry));
                
                // Calculate throughput (executions per second)
                double throughput = (double) batchSize / (processingTime / 1000.0);
                
                processingTimes.add(processingTime);
                throughputValues.add(throughput);
                successfulRuns++;
                
                // Clean up test data
                cleanupTestData(context.getValidatedExecutions());
                
            } catch (Exception e) {
                logger.warn("Benchmark iteration failed for batch size {}: {}", batchSize, e.getMessage());
                failedRuns++;
            }
        }
        
        return new BenchmarkResult(batchSize, processingTimes, throughputValues, successfulRuns, failedRuns);
    }
    
    /**
     * Generates test execution data for benchmarking.
     */
    private List<ExecutionPostDTO> generateTestExecutions(int count) {
        List<ExecutionPostDTO> executions = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ExecutionPostDTO execution = new ExecutionPostDTO();
            execution.setSecurityId(String.valueOf(1000 + (i % 100))); // Vary security IDs
            execution.setTradeType("BUY");
            execution.setExecutionStatus("NEW");
            execution.setDestination("NYSE");
            execution.setQuantity(BigDecimal.valueOf(100 + (i % 900))); // Vary quantities
            execution.setLimitPrice(BigDecimal.valueOf(50.0 + (i % 50))); // Vary prices
            execution.setVersion(1);
            executions.add(execution);
        }
        
        return executions;
    }
    
    /**
     * Cleans up test data after benchmarking.
     */
    private void cleanupTestData(List<Execution> executions) {
        try {
            if (!executions.isEmpty()) {
                executionRepository.deleteAll(executions);
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup test data: {}", e.getMessage());
        }
    }
    
    /**
     * Runs a concurrent load test to simulate real-world conditions.
     */
    public ConcurrentBenchmarkResults runConcurrentBenchmark(ConcurrentBenchmarkConfiguration config) {
        logger.info("Starting concurrent batch benchmark with {} threads, {} batches per thread",
                   config.getConcurrentThreads(), config.getBatchesPerThread());
        
        ExecutorService executor = Executors.newFixedThreadPool(config.getConcurrentThreads());
        List<CompletableFuture<List<BenchmarkResult>>> futures = new ArrayList<>();
        
        // Start concurrent benchmark threads
        for (int thread = 0; thread < config.getConcurrentThreads(); thread++) {
            final int threadId = thread;
            CompletableFuture<List<BenchmarkResult>> future = CompletableFuture.supplyAsync(() -> {
                List<BenchmarkResult> threadResults = new ArrayList<>();
                
                for (int batch = 0; batch < config.getBatchesPerThread(); batch++) {
                    try {
                        int batchSize = config.getBatchSize();
                        List<ExecutionPostDTO> testExecutions = generateTestExecutions(batchSize);
                        
                        long startTime = System.currentTimeMillis();
                        BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(testExecutions);
                        long processingTime = System.currentTimeMillis() - startTime;
                        
                        double throughput = (double) batchSize / (processingTime / 1000.0);
                        
                        BenchmarkResult result = new BenchmarkResult(batchSize, 
                                                                   List.of(processingTime), 
                                                                   List.of(throughput), 1, 0);
                        threadResults.add(result);
                        
                        cleanupTestData(context.getValidatedExecutions());
                        
                    } catch (Exception e) {
                        logger.warn("Concurrent benchmark failed for thread {}: {}", threadId, e.getMessage());
                    }
                }
                
                return threadResults;
            }, executor);
            
            futures.add(future);
        }
        
        // Collect results
        List<BenchmarkResult> allResults = new ArrayList<>();
        try {
            for (CompletableFuture<List<BenchmarkResult>> future : futures) {
                allResults.addAll(future.get(config.getTimeoutMinutes(), TimeUnit.MINUTES));
            }
        } catch (Exception e) {
            logger.error("Concurrent benchmark failed", e);
        } finally {
            executor.shutdown();
        }
        
        return new ConcurrentBenchmarkResults(allResults, config);
    }
    
    /**
     * Configuration for batch size benchmarking.
     */
    public static class BenchmarkConfiguration {
        private final int[] batchSizes;
        private final int iterationsPerBatchSize;
        
        public BenchmarkConfiguration(int[] batchSizes, int iterationsPerBatchSize) {
            this.batchSizes = batchSizes;
            this.iterationsPerBatchSize = iterationsPerBatchSize;
        }
        
        public int[] getBatchSizes() { return batchSizes; }
        public int getIterationsPerBatchSize() { return iterationsPerBatchSize; }
        
        @Override
        public String toString() {
            return String.format("BenchmarkConfiguration{batchSizes=%s, iterations=%d}", 
                               java.util.Arrays.toString(batchSizes), iterationsPerBatchSize);
        }
    }
    
    /**
     * Configuration for concurrent benchmarking.
     */
    public static class ConcurrentBenchmarkConfiguration {
        private final int concurrentThreads;
        private final int batchesPerThread;
        private final int batchSize;
        private final int timeoutMinutes;
        
        public ConcurrentBenchmarkConfiguration(int concurrentThreads, int batchesPerThread, 
                                              int batchSize, int timeoutMinutes) {
            this.concurrentThreads = concurrentThreads;
            this.batchesPerThread = batchesPerThread;
            this.batchSize = batchSize;
            this.timeoutMinutes = timeoutMinutes;
        }
        
        public int getConcurrentThreads() { return concurrentThreads; }
        public int getBatchesPerThread() { return batchesPerThread; }
        public int getBatchSize() { return batchSize; }
        public int getTimeoutMinutes() { return timeoutMinutes; }
    }
    
    /**
     * Results for a single batch size benchmark.
     */
    public static class BenchmarkResult {
        private final int batchSize;
        private final List<Long> processingTimes;
        private final List<Double> throughputValues;
        private final int successfulRuns;
        private final int failedRuns;
        
        public BenchmarkResult(int batchSize, List<Long> processingTimes, List<Double> throughputValues,
                             int successfulRuns, int failedRuns) {
            this.batchSize = batchSize;
            this.processingTimes = new ArrayList<>(processingTimes);
            this.throughputValues = new ArrayList<>(throughputValues);
            this.successfulRuns = successfulRuns;
            this.failedRuns = failedRuns;
        }
        
        public int getBatchSize() { return batchSize; }
        public List<Long> getProcessingTimes() { return processingTimes; }
        public List<Double> getThroughputValues() { return throughputValues; }
        public int getSuccessfulRuns() { return successfulRuns; }
        public int getFailedRuns() { return failedRuns; }
        
        public double getAverageProcessingTime() {
            return processingTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }
        
        public double getAverageThroughput() {
            return throughputValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        public double getSuccessRate() {
            int totalRuns = successfulRuns + failedRuns;
            return totalRuns > 0 ? (double) successfulRuns / totalRuns : 0.0;
        }
    }
    
    /**
     * Complete benchmark results with analysis.
     */
    public static class BenchmarkResults {
        private final List<BenchmarkResult> results;
        private final BenchmarkConfiguration configuration;
        
        public BenchmarkResults(List<BenchmarkResult> results, BenchmarkConfiguration configuration) {
            this.results = new ArrayList<>(results);
            this.configuration = configuration;
        }
        
        public List<BenchmarkResult> getResults() { return results; }
        public BenchmarkConfiguration getConfiguration() { return configuration; }
        
        public BenchmarkResult getBestThroughputResult() {
            return results.stream()
                    .max((r1, r2) -> Double.compare(r1.getAverageThroughput(), r2.getAverageThroughput()))
                    .orElse(null);
        }
        
        public BenchmarkResult getBestLatencyResult() {
            return results.stream()
                    .min((r1, r2) -> Double.compare(r1.getAverageProcessingTime(), r2.getAverageProcessingTime()))
                    .orElse(null);
        }
        
        public String getSummary() {
            BenchmarkResult bestThroughput = getBestThroughputResult();
            BenchmarkResult bestLatency = getBestLatencyResult();
            
            return String.format("Benchmark Summary: %d batch sizes tested, " +
                               "Best throughput: %d items (%.1f exec/sec), " +
                               "Best latency: %d items (%.1f ms)",
                               results.size(),
                               bestThroughput != null ? bestThroughput.getBatchSize() : 0,
                               bestThroughput != null ? bestThroughput.getAverageThroughput() : 0.0,
                               bestLatency != null ? bestLatency.getBatchSize() : 0,
                               bestLatency != null ? bestLatency.getAverageProcessingTime() : 0.0);
        }
    }
    
    /**
     * Results for concurrent benchmarking.
     */
    public static class ConcurrentBenchmarkResults {
        private final List<BenchmarkResult> results;
        private final ConcurrentBenchmarkConfiguration configuration;
        
        public ConcurrentBenchmarkResults(List<BenchmarkResult> results, ConcurrentBenchmarkConfiguration configuration) {
            this.results = new ArrayList<>(results);
            this.configuration = configuration;
        }
        
        public List<BenchmarkResult> getResults() { return results; }
        public ConcurrentBenchmarkConfiguration getConfiguration() { return configuration; }
        
        public double getOverallThroughput() {
            return results.stream().mapToDouble(BenchmarkResult::getAverageThroughput).sum();
        }
        
        public double getAverageLatency() {
            return results.stream().mapToDouble(BenchmarkResult::getAverageProcessingTime).average().orElse(0.0);
        }
        
        public double getOverallSuccessRate() {
            int totalSuccessful = results.stream().mapToInt(BenchmarkResult::getSuccessfulRuns).sum();
            int totalFailed = results.stream().mapToInt(BenchmarkResult::getFailedRuns).sum();
            int total = totalSuccessful + totalFailed;
            return total > 0 ? (double) totalSuccessful / total : 0.0;
        }
    }
}