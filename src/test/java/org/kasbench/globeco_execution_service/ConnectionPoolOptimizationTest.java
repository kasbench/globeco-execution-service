package org.kasbench.globeco_execution_service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for connection pool optimization.
 * Validates that HikariCP settings are properly configured for bulk operations.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "batch.execution.database.max-pool-size=15",
    "batch.execution.database.connection-timeout=10000",
    "batch.execution.database.max-lifetime=900000"
})
class ConnectionPoolOptimizationTest {

    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private ConnectionPoolMonitor connectionPoolMonitor;
    
    @Autowired
    private BatchExecutionProperties batchExecutionProperties;
    
    private HikariDataSource hikariDataSource;
    private HikariPoolMXBean poolMXBean;
    
    @BeforeEach
    void setUp() {
        assertTrue(dataSource instanceof HikariDataSource, 
                  "DataSource should be HikariDataSource for optimization tests");
        
        hikariDataSource = (HikariDataSource) dataSource;
        poolMXBean = hikariDataSource.getHikariPoolMXBean();
    }
    
    @Test
    void testConnectionPoolConfiguration() {
        // Verify that connection pool is configured with optimized settings
        assertEquals(15, hikariDataSource.getMaximumPoolSize(), 
                    "Max pool size should match configuration");
        assertEquals(10000, hikariDataSource.getConnectionTimeout(), 
                    "Connection timeout should match configuration");
        assertEquals(900000, hikariDataSource.getMaxLifetime(), 
                    "Max lifetime should match configuration");
        
        // Verify bulk operation optimizations
        assertFalse(hikariDataSource.isAutoCommit(), 
                   "Auto-commit should be disabled for bulk operations");
        assertTrue(hikariDataSource.isRegisterMbeans(), 
                  "MBeans should be registered for monitoring");
        assertEquals("HikariCP-BulkOperations", hikariDataSource.getPoolName(), 
                    "Pool name should be set for monitoring");
    }
    
    @Test
    void testConnectionAcquisitionPerformance() throws InterruptedException {
        int numberOfThreads = 10;
        int connectionsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulConnections = new AtomicInteger(0);
        AtomicInteger failedConnections = new AtomicInteger(0);
        List<Long> connectionTimes = new CopyOnWriteArrayList<>();
        
        // Test concurrent connection acquisition
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < connectionsPerThread; j++) {
                        long startTime = System.currentTimeMillis();
                        
                        try (Connection connection = dataSource.getConnection()) {
                            long connectionTime = System.currentTimeMillis() - startTime;
                            connectionTimes.add(connectionTime);
                            
                            // Verify connection is valid
                            assertTrue(connection.isValid(1), "Connection should be valid");
                            successfulConnections.incrementAndGet();
                            
                            // Simulate some work
                            Thread.sleep(10);
                            
                        } catch (SQLException e) {
                            failedConnections.incrementAndGet();
                            System.err.println("Failed to acquire connection: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), 
                  "All connection acquisition tasks should complete within 30 seconds");
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), 
                  "Executor should terminate within 10 seconds");
        
        // Verify results
        int expectedConnections = numberOfThreads * connectionsPerThread;
        assertEquals(expectedConnections, successfulConnections.get(), 
                    "All connection acquisitions should succeed");
        assertEquals(0, failedConnections.get(), 
                    "No connection acquisitions should fail");
        
        // Verify connection acquisition performance
        double averageConnectionTime = connectionTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        
        assertTrue(averageConnectionTime < 1000, 
                  "Average connection acquisition time should be less than 1 second, was: " + averageConnectionTime + "ms");
        
        // Verify no connections are leaked
        assertEquals(0, poolMXBean.getActiveConnections(), 
                    "No connections should be active after test completion");
    }
    
    @Test
    void testConnectionPoolUtilization() throws SQLException, InterruptedException {
        List<Connection> connections = new ArrayList<>();
        
        try {
            // Acquire connections up to 80% of pool capacity
            int targetConnections = (int) (hikariDataSource.getMaximumPoolSize() * 0.8);
            
            for (int i = 0; i < targetConnections; i++) {
                Connection connection = dataSource.getConnection();
                connections.add(connection);
            }
            
            // Verify pool utilization
            ConnectionPoolMonitor.ConnectionPoolStats stats = connectionPoolMonitor.getConnectionPoolStats();
            assertTrue(stats.getUtilization() >= 0.7, 
                      "Pool utilization should be at least 70%, was: " + (stats.getUtilization() * 100) + "%");
            assertTrue(stats.getUtilization() <= 1.0, 
                      "Pool utilization should not exceed 100%, was: " + (stats.getUtilization() * 100) + "%");
            
            assertEquals(targetConnections, stats.getActiveConnections(), 
                        "Active connections should match acquired connections");
            
        } finally {
            // Clean up connections
            for (Connection connection : connections) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        }
        
        // Wait for connections to be returned to pool
        Thread.sleep(100);
        
        // Verify connections are returned to pool
        assertEquals(0, poolMXBean.getActiveConnections(), 
                    "All connections should be returned to pool");
    }
    
    @Test
    void testConnectionPoolMonitoringMetrics() throws SQLException, InterruptedException {
        // Get initial stats
        ConnectionPoolMonitor.ConnectionPoolStats initialStats = connectionPoolMonitor.getConnectionPoolStats();
        
        // Acquire some connections
        List<Connection> connections = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                connections.add(dataSource.getConnection());
            }
            
            // Get updated stats
            ConnectionPoolMonitor.ConnectionPoolStats updatedStats = connectionPoolMonitor.getConnectionPoolStats();
            
            // Verify metrics are updated
            assertTrue(updatedStats.getActiveConnections() >= initialStats.getActiveConnections(), 
                      "Active connections should increase");
            assertTrue(updatedStats.getUtilization() >= initialStats.getUtilization(), 
                      "Utilization should increase");
            
            // Verify stats are reasonable
            assertTrue(updatedStats.getActiveConnections() <= hikariDataSource.getMaximumPoolSize(), 
                      "Active connections should not exceed max pool size");
            assertTrue(updatedStats.getTotalConnections() <= hikariDataSource.getMaximumPoolSize(), 
                      "Total connections should not exceed max pool size");
            assertTrue(updatedStats.getUtilization() >= 0.0 && updatedStats.getUtilization() <= 1.0, 
                      "Utilization should be between 0 and 1");
            
        } finally {
            // Clean up connections
            for (Connection connection : connections) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        }
    }
    
    @Test
    void testConnectionPoolHealthCheck() {
        // Test health check under normal conditions
        var health = connectionPoolMonitor.health();
        assertNotNull(health, "Health check should return a result");
        
        // Verify health details are present
        assertNotNull(health.getDetails().get("utilization"), "Utilization should be reported");
        assertNotNull(health.getDetails().get("activeConnections"), "Active connections should be reported");
        assertNotNull(health.getDetails().get("totalConnections"), "Total connections should be reported");
        assertNotNull(health.getDetails().get("maxPoolSize"), "Max pool size should be reported");
    }
    
    @Test
    void testBulkOperationConnectionSettings() {
        // Verify that connection pool settings are optimized for bulk operations
        BatchExecutionProperties.DatabaseProperties dbProps = batchExecutionProperties.getDatabase();
        
        assertEquals(15, dbProps.getMaxPoolSize(), 
                    "Max pool size should be configured for bulk operations");
        assertEquals(10000, dbProps.getConnectionTimeout(), 
                    "Connection timeout should be configured for bulk operations");
        assertEquals(900000, dbProps.getMaxLifetime(), 
                    "Max lifetime should be configured for bulk operations");
        
        // Verify HikariCP is configured to use these settings
        assertEquals(dbProps.getMaxPoolSize(), hikariDataSource.getMaximumPoolSize(), 
                    "HikariCP should use configured max pool size");
        assertEquals(dbProps.getConnectionTimeout(), hikariDataSource.getConnectionTimeout(), 
                    "HikariCP should use configured connection timeout");
        assertEquals(dbProps.getMaxLifetime(), hikariDataSource.getMaxLifetime(), 
                    "HikariCP should use configured max lifetime");
    }
    
    @Test
    void testConnectionPoolStressTest() throws InterruptedException {
        int numberOfThreads = 20;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);
        
        // Stress test the connection pool
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try (Connection connection = dataSource.getConnection()) {
                            // Simulate database work
                            connection.prepareStatement("SELECT 1").executeQuery();
                            successfulOperations.incrementAndGet();
                            
                            // Brief pause to simulate processing time
                            Thread.sleep(5);
                            
                        } catch (SQLException e) {
                            failedOperations.incrementAndGet();
                            System.err.println("Database operation failed: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for completion
        assertTrue(latch.await(60, TimeUnit.SECONDS), 
                  "Stress test should complete within 60 seconds");
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), 
                  "Executor should terminate within 10 seconds");
        
        // Verify results
        int expectedOperations = numberOfThreads * operationsPerThread;
        assertTrue(successfulOperations.get() >= expectedOperations * 0.95, 
                  "At least 95% of operations should succeed. Successful: " + successfulOperations.get() + 
                  ", Expected: " + expectedOperations);
        
        // Verify pool is stable after stress test
        assertEquals(0, poolMXBean.getActiveConnections(), 
                    "No connections should be active after stress test");
        assertTrue(poolMXBean.getIdleConnections() > 0, 
                  "Some connections should be idle after stress test");
    }
}