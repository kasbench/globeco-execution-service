package org.kasbench.globeco_execution_service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Monitors database connection pool metrics and updates BatchProcessingMetrics.
 * Tracks HikariCP connection pool usage, wait times, and performance metrics.
 */
@Component
public class DatabaseMetricsMonitor {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseMetricsMonitor.class);
    
    private final DataSource dataSource;
    private final BatchProcessingMetrics metrics;
    private HikariPoolMXBean hikariPoolMXBean;
    
    public DatabaseMetricsMonitor(DataSource dataSource, BatchProcessingMetrics metrics) {
        this.dataSource = dataSource;
        this.metrics = metrics;
        
        // Initialize HikariCP MXBean if available
        if (dataSource instanceof HikariDataSource) {
            try {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                this.hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();
                logger.info("HikariCP MXBean initialized for connection pool monitoring");
            } catch (Exception e) {
                logger.warn("Failed to initialize HikariCP MXBean: {}", e.getMessage());
            }
        } else {
            logger.warn("DataSource is not HikariDataSource, connection pool metrics will be limited");
        }
    }
    
    /**
     * Scheduled method to collect and update connection pool metrics.
     * Runs every 30 seconds to provide regular updates.
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void updateConnectionPoolMetrics() {
        if (hikariPoolMXBean == null) {
            logger.debug("HikariCP MXBean not available, skipping connection pool metrics update");
            return;
        }
        
        try {
            // Get current connection pool metrics
            int activeConnections = hikariPoolMXBean.getActiveConnections();
            int idleConnections = hikariPoolMXBean.getIdleConnections();
            int totalConnections = hikariPoolMXBean.getTotalConnections();
            int threadsAwaitingConnection = hikariPoolMXBean.getThreadsAwaitingConnection();
            
            // Calculate maximum connections from HikariDataSource
            int maxConnections = getMaximumPoolSize();
            
            // Update metrics
            metrics.updateConnectionPoolMetrics(activeConnections, maxConnections, threadsAwaitingConnection);
            
            // Log detailed metrics periodically (every 5 minutes)
            if (System.currentTimeMillis() % 300000 < 30000) { // Approximately every 5 minutes
                logDetailedConnectionMetrics(activeConnections, idleConnections, totalConnections, 
                                           threadsAwaitingConnection, maxConnections);
            }
            
            // Check for potential issues and log warnings
            checkConnectionPoolHealth(activeConnections, maxConnections, threadsAwaitingConnection);
            
        } catch (Exception e) {
            logger.error("Error updating connection pool metrics: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets the maximum pool size from the HikariDataSource configuration.
     */
    private int getMaximumPoolSize() {
        if (dataSource instanceof HikariDataSource) {
            return ((HikariDataSource) dataSource).getMaximumPoolSize();
        }
        return 20; // Default fallback
    }
    
    /**
     * Logs detailed connection pool metrics for monitoring and debugging.
     */
    private void logDetailedConnectionMetrics(int active, int idle, int total, 
                                            int awaiting, int max) {
        double utilization = max > 0 ? (double) active / max * 100.0 : 0.0;
        
        logger.info("Connection Pool Metrics - Active: {}, Idle: {}, Total: {}, Max: {}, " +
                   "Utilization: {:.1f}%, Threads Awaiting: {}", 
                   active, idle, total, max, utilization, awaiting);
    }
    
    /**
     * Checks connection pool health and logs warnings for potential issues.
     */
    private void checkConnectionPoolHealth(int active, int max, int awaiting) {
        double utilization = max > 0 ? (double) active / max * 100.0 : 0.0;
        
        // Warn if utilization is high
        if (utilization > 80.0) {
            logger.warn("High connection pool utilization: {:.1f}% ({}/{})", 
                       utilization, active, max);
        }
        
        // Warn if threads are waiting for connections
        if (awaiting > 0) {
            logger.warn("Threads awaiting database connections: {}", awaiting);
        }
        
        // Warn if pool is at maximum capacity
        if (active >= max) {
            logger.warn("Connection pool at maximum capacity: {}/{}", active, max);
        }
    }
    
    /**
     * Gets current connection pool statistics for monitoring dashboards.
     */
    public ConnectionPoolStats getConnectionPoolStats() {
        if (hikariPoolMXBean == null) {
            return new ConnectionPoolStats(0, 0, 0, 0, 0, false);
        }
        
        try {
            int active = hikariPoolMXBean.getActiveConnections();
            int idle = hikariPoolMXBean.getIdleConnections();
            int total = hikariPoolMXBean.getTotalConnections();
            int awaiting = hikariPoolMXBean.getThreadsAwaitingConnection();
            int max = getMaximumPoolSize();
            
            return new ConnectionPoolStats(active, idle, total, awaiting, max, true);
            
        } catch (Exception e) {
            logger.error("Error getting connection pool stats: {}", e.getMessage());
            return new ConnectionPoolStats(0, 0, 0, 0, 0, false);
        }
    }
    
    /**
     * Manually triggers a connection pool metrics update (for testing or admin purposes).
     */
    public void triggerManualUpdate() {
        logger.info("Manual connection pool metrics update triggered");
        updateConnectionPoolMetrics();
    }
    
    /**
     * Connection pool statistics for monitoring and alerting.
     */
    public static class ConnectionPoolStats {
        private final int activeConnections;
        private final int idleConnections;
        private final int totalConnections;
        private final int threadsAwaitingConnection;
        private final int maximumPoolSize;
        private final boolean metricsAvailable;
        
        public ConnectionPoolStats(int activeConnections, int idleConnections, 
                                 int totalConnections, int threadsAwaitingConnection,
                                 int maximumPoolSize, boolean metricsAvailable) {
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
            this.threadsAwaitingConnection = threadsAwaitingConnection;
            this.maximumPoolSize = maximumPoolSize;
            this.metricsAvailable = metricsAvailable;
        }
        
        // Getters
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getTotalConnections() { return totalConnections; }
        public int getThreadsAwaitingConnection() { return threadsAwaitingConnection; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public boolean isMetricsAvailable() { return metricsAvailable; }
        
        public double getUtilizationPercentage() {
            return maximumPoolSize > 0 ? (double) activeConnections / maximumPoolSize * 100.0 : 0.0;
        }
        
        public boolean isHealthy() {
            return metricsAvailable && 
                   getUtilizationPercentage() < 80.0 && 
                   threadsAwaitingConnection == 0 &&
                   activeConnections < maximumPoolSize;
        }
        
        @Override
        public String toString() {
            if (!metricsAvailable) {
                return "ConnectionPoolStats{metricsAvailable=false}";
            }
            
            return String.format(
                "ConnectionPoolStats{active=%d, idle=%d, total=%d, awaiting=%d, max=%d, util=%.1f%%}",
                activeConnections, idleConnections, totalConnections, 
                threadsAwaitingConnection, maximumPoolSize, getUtilizationPercentage()
            );
        }
    }
}