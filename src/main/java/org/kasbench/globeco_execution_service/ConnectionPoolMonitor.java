package org.kasbench.globeco_execution_service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors HikariCP connection pool metrics and provides health checks and alerting.
 * Tracks pool utilization, connection acquisition times, and potential issues.
 */
@Component
public class ConnectionPoolMonitor implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitor.class);
    
    private final HikariDataSource hikariDataSource;
    private final HikariPoolMXBean poolMXBean;
    private final MeterRegistry meterRegistry;
    private final BatchExecutionProperties batchExecutionProperties;
    
    // Alert thresholds
    private static final double HIGH_UTILIZATION_THRESHOLD = 0.8; // 80%
    private static final double CRITICAL_UTILIZATION_THRESHOLD = 0.95; // 95%
    private static final long SLOW_CONNECTION_THRESHOLD_MS = 5000; // 5 seconds
    
    // Metrics tracking
    private final AtomicLong connectionAcquisitionTimeouts = new AtomicLong(0);
    private final AtomicLong connectionLeaks = new AtomicLong(0);
    private final AtomicLong highUtilizationEvents = new AtomicLong(0);
    
    @Autowired
    public ConnectionPoolMonitor(DataSource dataSource, MeterRegistry meterRegistry, 
                               BatchExecutionProperties batchExecutionProperties) {
        if (!(dataSource instanceof HikariDataSource)) {
            throw new IllegalArgumentException("DataSource must be HikariDataSource for monitoring");
        }
        
        this.hikariDataSource = (HikariDataSource) dataSource;
        this.poolMXBean = hikariDataSource.getHikariPoolMXBean();
        this.meterRegistry = meterRegistry;
        this.batchExecutionProperties = batchExecutionProperties;
        
        registerMetrics();
    }
    
    /**
     * Registers connection pool metrics with Micrometer.
     */
    private void registerMetrics() {
        // Active connections gauge
        Gauge.builder("hikaricp.connections.active", this, monitor -> (double) monitor.poolMXBean.getActiveConnections())
                .description("Active connections in the pool")
                .register(meterRegistry);
        
        // Idle connections gauge
        Gauge.builder("hikaricp.connections.idle", this, monitor -> (double) monitor.poolMXBean.getIdleConnections())
                .description("Idle connections in the pool")
                .register(meterRegistry);
        
        // Total connections gauge
        Gauge.builder("hikaricp.connections.total", this, monitor -> (double) monitor.poolMXBean.getTotalConnections())
                .description("Total connections in the pool")
                .register(meterRegistry);
        
        // Threads awaiting connection gauge
        Gauge.builder("hikaricp.connections.pending", this, monitor -> (double) monitor.poolMXBean.getThreadsAwaitingConnection())
                .description("Threads awaiting connection")
                .register(meterRegistry);
        
        // Pool utilization percentage
        Gauge.builder("hikaricp.connections.utilization", this, monitor -> monitor.calculateUtilization())
                .description("Connection pool utilization percentage")
                .register(meterRegistry);
        
        // Connection acquisition timeout counter
        Gauge.builder("hikaricp.connections.acquisition.timeouts", this, monitor -> (double) monitor.connectionAcquisitionTimeouts.get())
                .description("Number of connection acquisition timeouts")
                .register(meterRegistry);
        
        // Connection leak counter
        Gauge.builder("hikaricp.connections.leaks", this, monitor -> (double) monitor.connectionLeaks.get())
                .description("Number of detected connection leaks")
                .register(meterRegistry);
        
        // High utilization events counter
        Gauge.builder("hikaricp.connections.high.utilization.events", this, monitor -> (double) monitor.highUtilizationEvents.get())
                .description("Number of high utilization events")
                .register(meterRegistry);
    }
    
    /**
     * Calculates current pool utilization as a percentage.
     */
    private double calculateUtilization() {
        int totalConnections = poolMXBean.getTotalConnections();
        int activeConnections = poolMXBean.getActiveConnections();
        
        if (totalConnections == 0) {
            return 0.0;
        }
        
        return (double) activeConnections / totalConnections;
    }
    
    /**
     * Scheduled monitoring task that checks pool health and logs alerts.
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorConnectionPool() {
        try {
            double utilization = calculateUtilization();
            int activeConnections = poolMXBean.getActiveConnections();
            int totalConnections = poolMXBean.getTotalConnections();
            int idleConnections = poolMXBean.getIdleConnections();
            int threadsAwaiting = poolMXBean.getThreadsAwaitingConnection();
            
            // Log current status at debug level
            logger.debug("Connection Pool Status: active={}, idle={}, total={}, utilization={:.2f}%, awaiting={}",
                        activeConnections, idleConnections, totalConnections, utilization * 100, threadsAwaiting);
            
            // Check for high utilization
            if (utilization >= CRITICAL_UTILIZATION_THRESHOLD) {
                highUtilizationEvents.incrementAndGet();
                logger.error("CRITICAL: Connection pool utilization is critically high: {:.2f}% " +
                           "(active={}, total={}, awaiting={}). Consider increasing pool size or investigating connection leaks.",
                           utilization * 100, activeConnections, totalConnections, threadsAwaiting);
            } else if (utilization >= HIGH_UTILIZATION_THRESHOLD) {
                highUtilizationEvents.incrementAndGet();
                logger.warn("WARNING: Connection pool utilization is high: {:.2f}% " +
                          "(active={}, total={}, awaiting={}). Monitor for potential issues.",
                          utilization * 100, activeConnections, totalConnections, threadsAwaiting);
            }
            
            // Check for threads waiting for connections
            if (threadsAwaiting > 0) {
                logger.warn("WARNING: {} threads are waiting for database connections. " +
                          "Pool utilization: {:.2f}%, consider tuning pool settings.",
                          threadsAwaiting, utilization * 100);
            }
            
            // Check for potential connection leaks
            if (activeConnections > 0 && idleConnections == 0 && threadsAwaiting > 5) {
                connectionLeaks.incrementAndGet();
                logger.error("POTENTIAL CONNECTION LEAK: All connections active, no idle connections, " +
                           "and {} threads waiting. Investigate for unclosed connections.",
                           threadsAwaiting);
            }
            
        } catch (Exception e) {
            logger.error("Error monitoring connection pool", e);
        }
    }
    
    /**
     * Records a connection acquisition timeout event.
     */
    public void recordConnectionTimeout() {
        connectionAcquisitionTimeouts.incrementAndGet();
        logger.error("Connection acquisition timeout occurred. Total timeouts: {}", 
                    connectionAcquisitionTimeouts.get());
    }
    
    /**
     * Records a connection leak detection event.
     */
    public void recordConnectionLeak() {
        connectionLeaks.incrementAndGet();
        logger.error("Connection leak detected. Total leaks: {}", connectionLeaks.get());
    }
    
    /**
     * Provides health check information for the connection pool.
     */
    @Override
    public Health health() {
        try {
            double utilization = calculateUtilization();
            int activeConnections = poolMXBean.getActiveConnections();
            int totalConnections = poolMXBean.getTotalConnections();
            int threadsAwaiting = poolMXBean.getThreadsAwaitingConnection();
            
            Health.Builder healthBuilder = new Health.Builder();
            
            // Determine health status
            if (utilization >= CRITICAL_UTILIZATION_THRESHOLD || threadsAwaiting > 10) {
                healthBuilder.down();
            } else if (utilization >= HIGH_UTILIZATION_THRESHOLD || threadsAwaiting > 5) {
                healthBuilder.status("WARNING");
            } else {
                healthBuilder.up();
            }
            
            return healthBuilder
                    .withDetail("utilization", String.format("%.2f%%", utilization * 100))
                    .withDetail("activeConnections", activeConnections)
                    .withDetail("totalConnections", totalConnections)
                    .withDetail("idleConnections", poolMXBean.getIdleConnections())
                    .withDetail("threadsAwaiting", threadsAwaiting)
                    .withDetail("maxPoolSize", batchExecutionProperties.getDatabase().getMaxPoolSize())
                    .withDetail("connectionTimeouts", connectionAcquisitionTimeouts.get())
                    .withDetail("connectionLeaks", connectionLeaks.get())
                    .withDetail("highUtilizationEvents", highUtilizationEvents.get())
                    .build();
                    
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", "Failed to check connection pool health: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Gets current connection pool statistics for monitoring dashboards.
     */
    public ConnectionPoolStats getConnectionPoolStats() {
        return new ConnectionPoolStats(
                poolMXBean.getActiveConnections(),
                poolMXBean.getIdleConnections(),
                poolMXBean.getTotalConnections(),
                poolMXBean.getThreadsAwaitingConnection(),
                calculateUtilization(),
                connectionAcquisitionTimeouts.get(),
                connectionLeaks.get(),
                highUtilizationEvents.get()
        );
    }
    
    /**
     * Data class for connection pool statistics.
     */
    public static class ConnectionPoolStats {
        private final int activeConnections;
        private final int idleConnections;
        private final int totalConnections;
        private final int threadsAwaiting;
        private final double utilization;
        private final long connectionTimeouts;
        private final long connectionLeaks;
        private final long highUtilizationEvents;
        
        public ConnectionPoolStats(int activeConnections, int idleConnections, int totalConnections,
                                 int threadsAwaiting, double utilization, long connectionTimeouts,
                                 long connectionLeaks, long highUtilizationEvents) {
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
            this.threadsAwaiting = threadsAwaiting;
            this.utilization = utilization;
            this.connectionTimeouts = connectionTimeouts;
            this.connectionLeaks = connectionLeaks;
            this.highUtilizationEvents = highUtilizationEvents;
        }
        
        // Getters
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getTotalConnections() { return totalConnections; }
        public int getThreadsAwaiting() { return threadsAwaiting; }
        public double getUtilization() { return utilization; }
        public long getConnectionTimeouts() { return connectionTimeouts; }
        public long getConnectionLeaks() { return connectionLeaks; }
        public long getHighUtilizationEvents() { return highUtilizationEvents; }
    }
}