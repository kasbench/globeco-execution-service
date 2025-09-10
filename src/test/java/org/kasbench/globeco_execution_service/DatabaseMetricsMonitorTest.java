package org.kasbench.globeco_execution_service;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DatabaseMetricsMonitor to verify connection pool metrics collection
 * and monitoring functionality.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseMetricsMonitorTest {
    
    @Mock
    private DataSource mockDataSource;
    
    @Mock
    private HikariDataSource mockHikariDataSource;
    
    private BatchProcessingMetrics metrics;
    private DatabaseMetricsMonitor monitor;
    
    @BeforeEach
    void setUp() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics = new BatchProcessingMetrics(meterRegistry);
    }
    
    @Test
    void testDatabaseMetricsMonitorWithNonHikariDataSource() {
        // Test with non-HikariCP DataSource
        monitor = new DatabaseMetricsMonitor(mockDataSource, metrics);
        
        // Should handle gracefully without HikariCP MXBean
        monitor.updateConnectionPoolMetrics();
        
        // Get stats should return unavailable metrics
        DatabaseMetricsMonitor.ConnectionPoolStats stats = monitor.getConnectionPoolStats();
        assertThat(stats.isMetricsAvailable()).isFalse();
        assertThat(stats.isHealthy()).isFalse();
    }
    
    @Test
    void testDatabaseMetricsMonitorWithHikariDataSource() {
        // Configure mock HikariDataSource
        lenient().when(mockHikariDataSource.getMaximumPoolSize()).thenReturn(20);
        
        monitor = new DatabaseMetricsMonitor(mockHikariDataSource, metrics);
        
        // Manual trigger should work without errors
        monitor.triggerManualUpdate();
        
        // Verify monitor was created successfully
        assertThat(monitor).isNotNull();
    }
    
    @Test
    void testConnectionPoolStatsCalculations() {
        // Test ConnectionPoolStats calculations
        DatabaseMetricsMonitor.ConnectionPoolStats stats = 
            new DatabaseMetricsMonitor.ConnectionPoolStats(15, 5, 20, 2, 25, true);
        
        assertThat(stats.getActiveConnections()).isEqualTo(15);
        assertThat(stats.getIdleConnections()).isEqualTo(5);
        assertThat(stats.getTotalConnections()).isEqualTo(20);
        assertThat(stats.getThreadsAwaitingConnection()).isEqualTo(2);
        assertThat(stats.getMaximumPoolSize()).isEqualTo(25);
        assertThat(stats.isMetricsAvailable()).isTrue();
        
        // Test utilization calculation
        assertThat(stats.getUtilizationPercentage()).isEqualTo(60.0); // 15/25 * 100
        
        // Test health check - should be unhealthy due to threads awaiting
        assertThat(stats.isHealthy()).isFalse();
    }
    
    @Test
    void testConnectionPoolStatsHealthyScenario() {
        // Test healthy connection pool scenario
        DatabaseMetricsMonitor.ConnectionPoolStats healthyStats = 
            new DatabaseMetricsMonitor.ConnectionPoolStats(10, 5, 15, 0, 20, true);
        
        assertThat(healthyStats.getUtilizationPercentage()).isEqualTo(50.0);
        assertThat(healthyStats.isHealthy()).isTrue();
    }
    
    @Test
    void testConnectionPoolStatsUnhealthyScenarios() {
        // Test high utilization scenario
        DatabaseMetricsMonitor.ConnectionPoolStats highUtilizationStats = 
            new DatabaseMetricsMonitor.ConnectionPoolStats(17, 3, 20, 0, 20, true);
        
        assertThat(highUtilizationStats.getUtilizationPercentage()).isEqualTo(85.0);
        assertThat(highUtilizationStats.isHealthy()).isFalse(); // > 80% utilization
        
        // Test threads awaiting scenario
        DatabaseMetricsMonitor.ConnectionPoolStats threadsAwaitingStats = 
            new DatabaseMetricsMonitor.ConnectionPoolStats(10, 5, 15, 3, 20, true);
        
        assertThat(threadsAwaitingStats.isHealthy()).isFalse(); // threads awaiting > 0
        
        // Test at maximum capacity scenario
        DatabaseMetricsMonitor.ConnectionPoolStats maxCapacityStats = 
            new DatabaseMetricsMonitor.ConnectionPoolStats(20, 0, 20, 0, 20, true);
        
        assertThat(maxCapacityStats.isHealthy()).isFalse(); // active >= max
    }
    
    @Test
    void testConnectionPoolStatsToString() {
        // Test toString for available metrics
        DatabaseMetricsMonitor.ConnectionPoolStats stats = 
            new DatabaseMetricsMonitor.ConnectionPoolStats(12, 8, 20, 1, 25, true);
        
        String statsString = stats.toString();
        assertThat(statsString).contains("active=12");
        assertThat(statsString).contains("idle=8");
        assertThat(statsString).contains("total=20");
        assertThat(statsString).contains("awaiting=1");
        assertThat(statsString).contains("max=25");
        assertThat(statsString).contains("util=48.0%");
        
        // Test toString for unavailable metrics
        DatabaseMetricsMonitor.ConnectionPoolStats unavailableStats = 
            new DatabaseMetricsMonitor.ConnectionPoolStats(0, 0, 0, 0, 0, false);
        
        String unavailableString = unavailableStats.toString();
        assertThat(unavailableString).contains("metricsAvailable=false");
    }
    
    @Test
    void testMetricsIntegration() {
        monitor = new DatabaseMetricsMonitor(mockDataSource, metrics);
        
        // Manually update metrics to test integration
        metrics.updateConnectionPoolMetrics(15, 20, 2);
        
        // Verify metrics were updated
        assertThat(metrics.getActiveConnections()).isEqualTo(15.0);
        assertThat(metrics.getMaxConnections()).isEqualTo(20.0);
        assertThat(metrics.getConnectionUtilization()).isEqualTo(75.0);
    }
}