package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance tests for Execution queries with large datasets.
 * 
 * Enable these tests by setting system property: -Dperformance.tests.enabled=true
 */
@SpringBootTest
@Testcontainers
@EnabledIfSystemProperty(named = "performance.tests.enabled", matches = "true")
class ExecutionPerformanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionService executionService;

    private static final int LARGE_DATASET_SIZE = 10000;
    private static final int PERFORMANCE_THRESHOLD_MS = 1000; // 1 second
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Only create large dataset if not already present
        if (executionRepository.count() < LARGE_DATASET_SIZE) {
            createLargeDataset();
        }
    }

    @Test
    void testFilteringPerformanceWithLargeDataset() {
        // Test filtering by execution status
        long startTime = System.currentTimeMillis();
        
        ExecutionQueryParams queryParams = new ExecutionQueryParams(
            0, 100, "NEW", null, null, null, "receivedTimestamp"
        );
        ExecutionPageDTO result = executionService.findExecutions(queryParams);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        assertTrue(executionTime < PERFORMANCE_THRESHOLD_MS, 
            "Query execution time (" + executionTime + "ms) exceeded threshold (" + PERFORMANCE_THRESHOLD_MS + "ms)");
        assertTrue(result.getContent().size() > 0, "Should return results");
    }

    @Test
    void testSortingPerformanceWithLargeDataset() {
        // Test multi-field sorting performance
        long startTime = System.currentTimeMillis();
        
        ExecutionQueryParams queryParams = new ExecutionQueryParams(
            0, 50, null, null, null, null, "receivedTimestamp,-id"
        );
        ExecutionPageDTO result = executionService.findExecutions(queryParams);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        assertTrue(executionTime < PERFORMANCE_THRESHOLD_MS, 
            "Sorting execution time (" + executionTime + "ms) exceeded threshold (" + PERFORMANCE_THRESHOLD_MS + "ms)");
        assertTrue(result.getContent().size() > 0, "Should return results");
    }

    @Test
    void testPaginationPerformanceWithLargeDataset() {
        // Test pagination performance with large offset
        long startTime = System.currentTimeMillis();
        
        ExecutionQueryParams queryParams = new ExecutionQueryParams(
            5000, 100, null, null, null, null, "id"
        );
        ExecutionPageDTO result = executionService.findExecutions(queryParams);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        assertTrue(executionTime < PERFORMANCE_THRESHOLD_MS * 2, // Allow more time for large offset
            "Pagination execution time (" + executionTime + "ms) exceeded threshold");
        assertTrue(result.getPagination().getTotalElements() >= LARGE_DATASET_SIZE, 
            "Should reflect total dataset size");
    }

    @Test
    void testIndexedQueryPerformance() {
        // Test performance of indexed queries
        long startTime = System.currentTimeMillis();
        
        List<Execution> results = executionRepository.findByExecutionStatusOptimized("NEW");
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        assertTrue(executionTime < 500, // Indexed query should be very fast
            "Indexed query execution time (" + executionTime + "ms) exceeded 500ms threshold");
        assertTrue(results.size() > 0, "Should return results");
    }

    @Test
    void testSecurityIdQueryPerformance() {
        // Test security ID query performance with limit
        long startTime = System.currentTimeMillis();
        
        List<Execution> results = executionRepository.findBySecurityIdWithLimit("SEC001", 100);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        assertTrue(executionTime < 300, // Should be very fast with index and limit
            "Security ID query execution time (" + executionTime + "ms) exceeded 300ms threshold");
    }

    @Test
    void testTimeRangeQueryPerformance() {
        // Test time range query performance
        OffsetDateTime startTime = OffsetDateTime.now().minusHours(24);
        OffsetDateTime endTime = OffsetDateTime.now();
        
        long queryStartTime = System.currentTimeMillis();
        
        List<Execution> results = executionRepository.findByTimeRangeOptimized(startTime, endTime);
        
        long executionTime = System.currentTimeMillis() - queryStartTime;
        
        assertTrue(executionTime < PERFORMANCE_THRESHOLD_MS,
            "Time range query execution time (" + executionTime + "ms) exceeded threshold");
    }

    @Test
    void testCountQueryPerformance() {
        // Test count query performance
        long startTime = System.currentTimeMillis();
        
        long count = executionRepository.countByExecutionStatusOptimized("NEW");
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        assertTrue(executionTime < 200, // Count queries should be very fast
            "Count query execution time (" + executionTime + "ms) exceeded 200ms threshold");
        assertTrue(count > 0, "Should have count > 0");
    }

    private void createLargeDataset() {
        System.out.println("Creating large dataset for performance testing...");
        
        List<Execution> executions = new ArrayList<>();
        Random random = new Random();
        String[] statuses = {"NEW", "PARTIALLY_FILLED", "FILLED", "CANCELLED"};
        String[] tradeTypes = {"BUY", "SELL"};
        String[] destinations = {"NYSE", "NASDAQ", "CBOE", "IEX"};
        
        for (int i = 0; i < LARGE_DATASET_SIZE; i++) {
            Execution execution = new Execution();
            execution.setExecutionStatus(statuses[random.nextInt(statuses.length)]);
            execution.setTradeType(tradeTypes[random.nextInt(tradeTypes.length)]);
            execution.setDestination(destinations[random.nextInt(destinations.length)]);
            execution.setSecurityId("SEC" + String.format("%03d", random.nextInt(100))); // 100 different securities
            execution.setQuantity(BigDecimal.valueOf(random.nextInt(1000) + 1));
            execution.setLimitPrice(BigDecimal.valueOf(random.nextDouble() * 100));
            execution.setReceivedTimestamp(OffsetDateTime.now().minusMinutes(random.nextInt(10080))); // Up to 7 days ago
            execution.setSentTimestamp(execution.getReceivedTimestamp().plusSeconds(random.nextInt(60)));
            execution.setTradeServiceExecutionId(random.nextInt(100000));
            execution.setQuantityFilled(BigDecimal.valueOf(random.nextInt(execution.getQuantity().intValue() + 1)));
            execution.setAveragePrice(execution.getLimitPrice().multiply(BigDecimal.valueOf(0.9 + random.nextDouble() * 0.2)));
            
            executions.add(execution);
            
            // Batch save every 1000 records to avoid memory issues
            if (executions.size() == 1000) {
                executionRepository.saveAll(executions);
                executions.clear();
                System.out.println("Created " + (i + 1) + " executions...");
            }
        }
        
        // Save remaining executions
        if (!executions.isEmpty()) {
            executionRepository.saveAll(executions);
        }
        
        System.out.println("Large dataset creation completed: " + LARGE_DATASET_SIZE + " executions");
    }
} 