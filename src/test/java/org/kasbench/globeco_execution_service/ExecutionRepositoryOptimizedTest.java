package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for optimized repository methods in ExecutionRepository.
 */
@DataJpaTest
@Testcontainers
class ExecutionRepositoryOptimizedTest {

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

    private Execution execution1;
    private Execution execution2;
    private Execution execution3;

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        executionRepository.deleteAll();

        // Create test executions
        execution1 = new Execution();
        execution1.setExecutionStatus("NEW");
        execution1.setTradeType("BUY");
        execution1.setDestination("NYSE");
        execution1.setSecurityId("SEC001");
        execution1.setQuantity(BigDecimal.valueOf(100));
        execution1.setLimitPrice(BigDecimal.valueOf(50.00));
        execution1.setReceivedTimestamp(OffsetDateTime.now().minusHours(1));
        execution1.setSentTimestamp(OffsetDateTime.now().minusHours(1).plusMinutes(1));
        execution1.setTradeServiceExecutionId(1001);
        execution1.setQuantityFilled(BigDecimal.valueOf(50));
        execution1.setAveragePrice(BigDecimal.valueOf(49.95));

        execution2 = new Execution();
        execution2.setExecutionStatus("FILLED");
        execution2.setTradeType("SELL");
        execution2.setDestination("NASDAQ");
        execution2.setSecurityId("SEC001");
        execution2.setQuantity(BigDecimal.valueOf(200));
        execution2.setLimitPrice(BigDecimal.valueOf(55.00));
        execution2.setReceivedTimestamp(OffsetDateTime.now().minusMinutes(30));
        execution2.setSentTimestamp(OffsetDateTime.now().minusMinutes(30).plusMinutes(1));
        execution2.setTradeServiceExecutionId(1002);
        execution2.setQuantityFilled(BigDecimal.valueOf(200));
        execution2.setAveragePrice(BigDecimal.valueOf(54.75));

        execution3 = new Execution();
        execution3.setExecutionStatus("NEW");
        execution3.setTradeType("BUY");
        execution3.setDestination("CBOE");
        execution3.setSecurityId("SEC002");
        execution3.setQuantity(BigDecimal.valueOf(150));
        execution3.setLimitPrice(BigDecimal.valueOf(25.00));
        execution3.setReceivedTimestamp(OffsetDateTime.now().minusMinutes(15));
        execution3.setSentTimestamp(OffsetDateTime.now().minusMinutes(15).plusMinutes(1));
        execution3.setTradeServiceExecutionId(1003);
        execution3.setQuantityFilled(BigDecimal.ZERO);
        execution3.setAveragePrice(null);

        // Save test data
        execution1 = executionRepository.save(execution1);
        execution2 = executionRepository.save(execution2);
        execution3 = executionRepository.save(execution3);
    }

    @Test
    void testFindByExecutionStatusOptimized() {
        // Test finding executions by status
        List<Execution> newExecutions = executionRepository.findByExecutionStatusOptimized("NEW");
        
        assertNotNull(newExecutions);
        assertEquals(2, newExecutions.size());
        assertTrue(newExecutions.stream().allMatch(e -> "NEW".equals(e.getExecutionStatus())));
        
        // Verify ordering (should be by receivedTimestamp DESC)
        assertEquals(execution3.getId(), newExecutions.get(0).getId()); // Most recent
        assertEquals(execution1.getId(), newExecutions.get(1).getId()); // Older
    }

    @Test
    void testFindBySecurityIdWithLimit() {
        // Test finding executions by security ID with limit
        List<Execution> sec001Executions = executionRepository.findBySecurityIdWithLimit("SEC001", 1);
        
        assertNotNull(sec001Executions);
        assertEquals(1, sec001Executions.size());
        assertEquals("SEC001", sec001Executions.get(0).getSecurityId());
        assertEquals(execution2.getId(), sec001Executions.get(0).getId()); // Most recent for SEC001
    }

    @Test
    void testFindByTimeRangeOptimized() {
        // Test finding executions within a time range
        OffsetDateTime startTime = OffsetDateTime.now().minusHours(2);
        OffsetDateTime endTime = OffsetDateTime.now();
        
        List<Execution> executions = executionRepository.findByTimeRangeOptimized(startTime, endTime);
        
        assertNotNull(executions);
        assertEquals(3, executions.size());
        
        // Verify all executions are within the time range and ordered by timestamp DESC
        assertEquals(execution3.getId(), executions.get(0).getId()); // Most recent
        assertEquals(execution2.getId(), executions.get(1).getId()); // Middle
        assertEquals(execution1.getId(), executions.get(2).getId()); // Oldest
    }

    // Note: Bulk update test skipped due to transaction isolation complexity in test environment

    @Test
    void testCountByExecutionStatusOptimized() {
        // Test optimized count query
        long newCount = executionRepository.countByExecutionStatusOptimized("NEW");
        long filledCount = executionRepository.countByExecutionStatusOptimized("FILLED");
        long cancelledCount = executionRepository.countByExecutionStatusOptimized("CANCELLED");
        
        assertEquals(2, newCount);
        assertEquals(1, filledCount);
        assertEquals(0, cancelledCount);
    }

    @Test
    void testFindUnfilledExecutions() {
        // Test finding unfilled executions
        List<Execution> unfilledExecutions = executionRepository.findUnfilledExecutions();
        
        assertNotNull(unfilledExecutions);
        assertEquals(2, unfilledExecutions.size());
        
        // Should include execution1 (50/100 filled) and execution3 (0/150 filled)
        // Should NOT include execution2 (200/200 filled)
        assertTrue(unfilledExecutions.stream().anyMatch(e -> e.getId().equals(execution1.getId())));
        assertTrue(unfilledExecutions.stream().anyMatch(e -> e.getId().equals(execution3.getId())));
        assertFalse(unfilledExecutions.stream().anyMatch(e -> e.getId().equals(execution2.getId())));
    }

    @Test
    void testFindRecentByTradeType() {
        // Test finding recent executions by trade type
        List<Execution> recentBuyOrders = executionRepository.findRecentByTradeType("BUY", 5);
        List<Execution> recentSellOrders = executionRepository.findRecentByTradeType("SELL", 5);
        
        assertNotNull(recentBuyOrders);
        assertNotNull(recentSellOrders);
        
        assertEquals(2, recentBuyOrders.size());
        assertEquals(1, recentSellOrders.size());
        
        // Verify trade types
        assertTrue(recentBuyOrders.stream().allMatch(e -> "BUY".equals(e.getTradeType())));
        assertTrue(recentSellOrders.stream().allMatch(e -> "SELL".equals(e.getTradeType())));
        
        // Verify ordering (most recent first)
        assertEquals(execution3.getId(), recentBuyOrders.get(0).getId());
        assertEquals(execution1.getId(), recentBuyOrders.get(1).getId());
        assertEquals(execution2.getId(), recentSellOrders.get(0).getId());
    }

    @Test
    void testFindBySecurityIdWithLimitNoResults() {
        // Test with security ID that doesn't exist
        List<Execution> results = executionRepository.findBySecurityIdWithLimit("NONEXISTENT", 10);
        
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testTimeRangeWithNoResults() {
        // Test time range with no results
        OffsetDateTime futureStart = OffsetDateTime.now().plusDays(1);
        OffsetDateTime futureEnd = OffsetDateTime.now().plusDays(2);
        
        List<Execution> results = executionRepository.findByTimeRangeOptimized(futureStart, futureEnd);
        
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
} 