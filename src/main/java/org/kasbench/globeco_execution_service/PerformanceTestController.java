package org.kasbench.globeco_execution_service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for performance testing and benchmarking the executions API.
 * This should only be enabled in non-production environments.
 */
@RestController
@RequestMapping("/api/v1/performance-test")
@Tag(name = "Performance Testing", description = "Endpoints for performance testing and benchmarking")
public class PerformanceTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestController.class);
    
    private final ExecutionService executionService;
    
    public PerformanceTestController(ExecutionService executionService) {
        this.executionService = executionService;
    }
    
    /**
     * Run a performance test on the executions endpoint with various parameters.
     */
    @Operation(
        summary = "Run performance test on executions endpoint",
        description = "Tests the /api/v1/executions endpoint with various parameters to identify performance bottlenecks"
    )
    @GetMapping("/executions-benchmark")
    public Map<String, Object> benchmarkExecutionsEndpoint(
            @Parameter(description = "Number of test iterations", example = "10")
            @RequestParam(value = "iterations", defaultValue = "10") int iterations,
            @Parameter(description = "Page size to test", example = "50")
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
            @Parameter(description = "Test with status filter", example = "NEW")
            @RequestParam(value = "testStatus", required = false) String testStatus,
            @Parameter(description = "Test with ticker filter", example = "AAPL")
            @RequestParam(value = "testTicker", required = false) String testTicker) {
        
        logger.info("Starting performance benchmark - iterations: {}, pageSize: {}, status: {}, ticker: {}", 
            iterations, pageSize, testStatus, testTicker);
        
        Map<String, Object> results = new HashMap<>();
        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        
        // Test scenarios
        Map<String, Long> scenarioTimes = new HashMap<>();
        
        // Scenario 1: No filters (baseline)
        long baselineTime = runScenario("No filters", iterations, pageSize, null, null, null, null);
        scenarioTimes.put("baseline_no_filters", baselineTime);
        
        // Scenario 2: Status filter only
        if (testStatus != null) {
            long statusTime = runScenario("Status filter", iterations, pageSize, testStatus, null, null, null);
            scenarioTimes.put("status_filter_only", statusTime);
        }
        
        // Scenario 3: Ticker filter only (most expensive due to security service call)
        if (testTicker != null) {
            long tickerTime = runScenario("Ticker filter", iterations, pageSize, null, null, null, testTicker);
            scenarioTimes.put("ticker_filter_only", tickerTime);
        }
        
        // Scenario 4: Multiple filters
        if (testStatus != null && testTicker != null) {
            long multiTime = runScenario("Multiple filters", iterations, pageSize, testStatus, "BUY", "NYSE", testTicker);
            scenarioTimes.put("multiple_filters", multiTime);
        }
        
        // Scenario 5: Large page size
        long largePageTime = runScenario("Large page", iterations, 100, null, null, null, null);
        scenarioTimes.put("large_page_size", largePageTime);
        
        results.put("test_parameters", Map.of(
            "iterations", iterations,
            "page_size", pageSize,
            "test_status", testStatus,
            "test_ticker", testTicker
        ));
        
        results.put("scenario_times_ms", scenarioTimes);
        results.put("recommendations", generateRecommendations(scenarioTimes));
        
        logger.info("Performance benchmark completed - results: {}", results);
        
        return results;
    }
    
    private long runScenario(String scenarioName, int iterations, int pageSize, 
                           String status, String tradeType, String destination, String ticker) {
        logger.info("Running scenario: {} ({} iterations)", scenarioName, iterations);
        
        long totalTime = 0;
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            
            try {
                ExecutionQueryParams params = new ExecutionQueryParams(
                    0, pageSize, status, tradeType, destination, ticker, "id"
                );
                
                ExecutionPageDTO result = executionService.findExecutions(params);
                
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                totalTime += duration;
                
                if (i == 0) {
                    logger.debug("Scenario '{}' first iteration: {}ms, returned {} results", 
                        scenarioName, duration, result.getContent().size());
                }
                
            } catch (Exception e) {
                logger.error("Error in scenario '{}' iteration {}: {}", scenarioName, i, e.getMessage());
            }
        }
        
        long avgTime = totalTime / iterations;
        logger.info("Scenario '{}' completed - Average: {}ms, Total: {}ms", scenarioName, avgTime, totalTime);
        
        return avgTime;
    }
    
    private Map<String, String> generateRecommendations(Map<String, Long> scenarioTimes) {
        Map<String, String> recommendations = new HashMap<>();
        
        Long baseline = scenarioTimes.get("baseline_no_filters");
        Long tickerFilter = scenarioTimes.get("ticker_filter_only");
        Long statusFilter = scenarioTimes.get("status_filter_only");
        Long largePageSize = scenarioTimes.get("large_page_size");
        
        if (baseline != null && baseline > 1000) {
            recommendations.put("slow_baseline", 
                "Baseline query is slow (>" + baseline + "ms). Check database indexes and connection pool.");
        }
        
        if (tickerFilter != null && baseline != null && tickerFilter > baseline * 3) {
            recommendations.put("slow_ticker_resolution", 
                "Ticker filtering is significantly slower. Check Security Service performance and caching.");
        }
        
        if (statusFilter != null && baseline != null && statusFilter > baseline * 2) {
            recommendations.put("slow_status_filter", 
                "Status filtering is slower than expected. Check execution_status index.");
        }
        
        if (largePageSize != null && baseline != null && largePageSize > baseline * 3) {
            recommendations.put("poor_pagination_scaling", 
                "Large page sizes scale poorly. Consider limiting max page size or optimizing queries.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.put("performance_ok", "All scenarios performed within acceptable ranges.");
        }
        
        return recommendations;
    }
}