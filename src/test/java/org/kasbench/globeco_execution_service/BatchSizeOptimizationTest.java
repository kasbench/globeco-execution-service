package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for batch size optimization functionality.
 * Validates dynamic batch sizing, performance tracking, and optimization logic.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BatchSizeOptimizationTest {

    @Mock
    private ConnectionPoolMonitor connectionPoolMonitor;

    private BatchExecutionProperties batchExecutionProperties;
    private BatchSizeOptimizer batchSizeOptimizer;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // Set up batch execution properties
        batchExecutionProperties = new BatchExecutionProperties();
        batchExecutionProperties.setBulkInsertBatchSize(500);
        
        BatchExecutionProperties.PerformanceProperties perfProps = new BatchExecutionProperties.PerformanceProperties();
        perfProps.setEnableDynamicBatchSizing(true);
        perfProps.setMinBatchSize(50);
        perfProps.setMaxBatchSize(2000);
        batchExecutionProperties.setPerformance(perfProps);

        meterRegistry = new SimpleMeterRegistry();
        batchSizeOptimizer = new BatchSizeOptimizer(batchExecutionProperties, connectionPoolMonitor, meterRegistry);
    }

    @Test
    void testGetOptimalBatchSizeWithDynamicSizingDisabled() {
        // Disable dynamic sizing
        batchExecutionProperties.getPerformance().setEnableDynamicBatchSizing(false);
        batchSizeOptimizer = new BatchSizeOptimizer(batchExecutionProperties, connectionPoolMonitor, meterRegistry);

        // Test that it returns the configured batch size
        int optimalSize = batchSizeOptimizer.getOptimalBatchSize(1000);
        assertEquals(500, optimalSize, "Should return configured batch size when dynamic sizing is disabled");

        int smallerSize = batchSizeOptimizer.getOptimalBatchSize(300);
        assertEquals(300, smallerSize, "Should return requested size if smaller than configured size");
    }

    @Test
    void testGetOptimalBatchSizeWithDynamicSizingEnabled() {
        // Test initial optimal size
        int optimalSize = batchSizeOptimizer.getOptimalBatchSize(1000);
        assertEquals(500, optimalSize, "Should return configured batch size initially");

        // Test with smaller requested size
        int smallerSize = batchSizeOptimizer.getOptimalBatchSize(300);
        assertEquals(300, smallerSize, "Should return requested size if smaller than optimal");
    }

    @Test
    void testBatchSplitCalculation() {
        // Test single batch (no splitting needed)
        int[] splits = batchSizeOptimizer.calculateBatchSplits(400);
        assertArrayEquals(new int[]{400}, splits, "Should not split batch smaller than optimal size");

        // Test multiple batches
        int[] largeSplits = batchSizeOptimizer.calculateBatchSplits(1200);
        assertEquals(3, largeSplits.length, "Should split large batch into multiple chunks");
        assertEquals(500, largeSplits[0], "First batch should be optimal size");
        assertEquals(500, largeSplits[1], "Second batch should be optimal size");
        assertEquals(200, largeSplits[2], "Last batch should contain remaining items");

        // Verify total
        int total = java.util.Arrays.stream(largeSplits).sum();
        assertEquals(1200, total, "Sum of splits should equal original size");
    }

    @Test
    void testBatchPerformanceRecordingWithoutOptimization() {
        // Record performance but don't trigger optimization (too soon)
        batchSizeOptimizer.recordBatchPerformance(500, 2000, true);

        BatchSizeOptimizer.BatchSizeStats stats = batchSizeOptimizer.getBatchSizeStats();
        assertEquals(1, stats.getTotalBatchesProcessed(), "Should record batch processing");
        assertEquals(2000.0, stats.getAverageProcessingTime(), 0.1, "Should record processing time");
        assertEquals(500, stats.getCurrentOptimalSize(), "Optimal size should not change without optimization");
    }

    @Test
    void testBatchSizeOptimizationOnHighConnectionUtilization() {
        // Mock high connection utilization
        ConnectionPoolMonitor.ConnectionPoolStats poolStats = 
                new ConnectionPoolMonitor.ConnectionPoolStats(18, 2, 20, 0, 0.9, 0, 0, 0);
        when(connectionPoolMonitor.getConnectionPoolStats()).thenReturn(poolStats);

        // Force optimization by setting last optimization time to past
        batchSizeOptimizer.resetStats();
        
        // Simulate time passing to trigger optimization
        try {
            Thread.sleep(100); // Brief pause to ensure time difference
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Record performance that should trigger optimization
        batchSizeOptimizer.recordBatchPerformance(500, 5000, true);

        // Wait a bit more and record another batch to trigger optimization
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // This should trigger optimization due to high utilization
        batchSizeOptimizer.recordBatchPerformance(500, 8000, true);

        BatchSizeOptimizer.BatchSizeStats stats = batchSizeOptimizer.getBatchSizeStats();
        assertTrue(stats.getCurrentOptimalSize() < 500, 
                  "Optimal size should decrease due to high connection utilization");
    }

    @Test
    void testBatchSizeOptimizationOnLowUtilization() {
        // Mock low connection utilization
        ConnectionPoolMonitor.ConnectionPoolStats poolStats = 
                new ConnectionPoolMonitor.ConnectionPoolStats(5, 15, 20, 0, 0.25, 0, 0, 0);
        when(connectionPoolMonitor.getConnectionPoolStats()).thenReturn(poolStats);

        batchSizeOptimizer.resetStats();
        
        // Simulate good performance conditions
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        batchSizeOptimizer.recordBatchPerformance(500, 3000, true); // Good performance
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        batchSizeOptimizer.recordBatchPerformance(500, 2500, true); // Even better performance

        BatchSizeOptimizer.BatchSizeStats stats = batchSizeOptimizer.getBatchSizeStats();
        assertTrue(stats.getCurrentOptimalSize() >= 500, 
                  "Optimal size should stay same or increase due to low utilization and good performance");
    }

    @Test
    void testBatchSizeConstraints() {
        // Test that optimization respects min/max constraints
        BatchExecutionProperties.PerformanceProperties perfProps = batchExecutionProperties.getPerformance();
        perfProps.setMinBatchSize(100);
        perfProps.setMaxBatchSize(1000);

        // Mock critical connection utilization to force aggressive decrease
        ConnectionPoolMonitor.ConnectionPoolStats poolStats = 
                new ConnectionPoolMonitor.ConnectionPoolStats(19, 1, 20, 5, 0.95, 0, 0, 0);
        when(connectionPoolMonitor.getConnectionPoolStats()).thenReturn(poolStats);

        batchSizeOptimizer.resetStats();
        
        // Force multiple optimizations to drive size down
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            batchSizeOptimizer.recordBatchPerformance(500, 15000, false); // Failed batches
        }

        BatchSizeOptimizer.BatchSizeStats stats = batchSizeOptimizer.getBatchSizeStats();
        assertTrue(stats.getCurrentOptimalSize() >= perfProps.getMinBatchSize(), 
                  "Optimal size should not go below minimum");
        assertTrue(stats.getCurrentOptimalSize() <= perfProps.getMaxBatchSize(), 
                  "Optimal size should not exceed maximum");
    }

    @Test
    void testBatchSizeStatsAccuracy() {
        // Record multiple batches with different performance
        batchSizeOptimizer.recordBatchPerformance(500, 2000, true);
        batchSizeOptimizer.recordBatchPerformance(300, 1500, true);
        batchSizeOptimizer.recordBatchPerformance(700, 3000, false);

        BatchSizeOptimizer.BatchSizeStats stats = batchSizeOptimizer.getBatchSizeStats();
        
        assertEquals(3, stats.getTotalBatchesProcessed(), "Should track total batches processed");
        assertEquals(2166.67, stats.getAverageProcessingTime(), 0.1, "Should calculate correct average processing time");
        assertEquals(500, stats.getConfiguredBaseSize(), "Should report configured base size");
        assertEquals(50, stats.getMinSize(), "Should report minimum size");
        assertEquals(2000, stats.getMaxSize(), "Should report maximum size");
        assertTrue(stats.isDynamicSizingEnabled(), "Should report dynamic sizing status");
    }

    @Test
    void testResetStats() {
        // Record some performance data
        batchSizeOptimizer.recordBatchPerformance(500, 2000, true);
        batchSizeOptimizer.recordBatchPerformance(300, 1500, true);

        BatchSizeOptimizer.BatchSizeStats statsBefore = batchSizeOptimizer.getBatchSizeStats();
        assertEquals(2, statsBefore.getTotalBatchesProcessed(), "Should have recorded batches");

        // Reset stats
        batchSizeOptimizer.resetStats();

        BatchSizeOptimizer.BatchSizeStats statsAfter = batchSizeOptimizer.getBatchSizeStats();
        assertEquals(0, statsAfter.getTotalBatchesProcessed(), "Should reset batch count");
        assertEquals(0.0, statsAfter.getAverageProcessingTime(), 0.1, "Should reset average processing time");
        assertEquals(500, statsAfter.getCurrentOptimalSize(), "Should reset optimal size to configured base");
    }

    @Test
    void testMetricsRegistration() {
        // Verify that metrics are registered
        assertNotNull(meterRegistry.find("batch.size.optimal.current").gauge(), 
                     "Should register optimal batch size gauge");
        assertNotNull(meterRegistry.find("batch.processing.time.average").gauge(), 
                     "Should register average processing time gauge");
        assertNotNull(meterRegistry.find("batch.processing.total.count").gauge(), 
                     "Should register total batch count gauge");

        // Record some performance and verify metrics are updated
        batchSizeOptimizer.recordBatchPerformance(500, 2000, true);

        assertEquals(500.0, meterRegistry.find("batch.size.optimal.current").gauge().value(), 0.1,
                    "Optimal size metric should be updated");
        assertEquals(1.0, meterRegistry.find("batch.processing.total.count").gauge().value(), 0.1,
                    "Total count metric should be updated");
        assertEquals(2000.0, meterRegistry.find("batch.processing.time.average").gauge().value(), 0.1,
                    "Average time metric should be updated");
    }

    @Test
    void testConcurrentBatchSizeAccess() throws InterruptedException {
        // Test thread safety of batch size optimization
        int numberOfThreads = 10;
        Thread[] threads = new Thread[numberOfThreads];
        
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    int optimalSize = batchSizeOptimizer.getOptimalBatchSize(500 + threadId * 10);
                    assertTrue(optimalSize > 0, "Optimal size should be positive");
                    
                    batchSizeOptimizer.recordBatchPerformance(optimalSize, 1000 + j * 100, true);
                    
                    int[] splits = batchSizeOptimizer.calculateBatchSplits(1000 + threadId * 100);
                    assertTrue(splits.length > 0, "Should return at least one split");
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }

        // Verify final state is consistent
        BatchSizeOptimizer.BatchSizeStats stats = batchSizeOptimizer.getBatchSizeStats();
        assertEquals(numberOfThreads * 10, stats.getTotalBatchesProcessed(), 
                    "Should have processed all batches from all threads");
        assertTrue(stats.getCurrentOptimalSize() > 0, "Optimal size should be positive");
    }
}