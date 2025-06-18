package org.kasbench.globeco_execution_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import java.util.HashMap;
import java.util.Map;

/**
 * Component for monitoring query performance metrics using Hibernate statistics.
 */
@Component
public class QueryPerformanceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryPerformanceMonitor.class);
    
    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;
    
    /**
     * Get Hibernate statistics for performance monitoring.
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            Statistics stats = sessionFactory.getStatistics();
            
            if (!stats.isStatisticsEnabled()) {
                logger.warn("Hibernate statistics are not enabled. Enable with hibernate.generate_statistics=true");
                metrics.put("statistics_enabled", false);
                return metrics;
            }
            
            // Query execution metrics
            metrics.put("query_execution_count", stats.getQueryExecutionCount());
            metrics.put("query_execution_max_time", stats.getQueryExecutionMaxTime());
            metrics.put("query_execution_max_time_query", stats.getQueryExecutionMaxTimeQueryString());
            metrics.put("slow_query_threshold_exceeded", stats.getQueryExecutionMaxTime() > 1000); // 1 second threshold
            
            // Entity metrics
            metrics.put("entity_load_count", stats.getEntityLoadCount());
            metrics.put("entity_insert_count", stats.getEntityInsertCount());
            metrics.put("entity_update_count", stats.getEntityUpdateCount());
            metrics.put("entity_delete_count", stats.getEntityDeleteCount());
            
            // Cache metrics
            metrics.put("second_level_cache_hit_count", stats.getSecondLevelCacheHitCount());
            metrics.put("second_level_cache_miss_count", stats.getSecondLevelCacheMissCount());
            metrics.put("second_level_cache_put_count", stats.getSecondLevelCachePutCount());
            
            // Calculate cache hit ratio
            long totalCacheHits = stats.getSecondLevelCacheHitCount();
            long totalCacheMisses = stats.getSecondLevelCacheMissCount();
            long totalCacheAccess = totalCacheHits + totalCacheMisses;
            double cacheHitRatio = totalCacheAccess > 0 ? (double) totalCacheHits / totalCacheAccess : 0.0;
            metrics.put("cache_hit_ratio", Math.round(cacheHitRatio * 100.0) / 100.0);
            
            // Connection metrics
            metrics.put("connection_count", stats.getConnectCount());
            metrics.put("prepared_statement_count", stats.getPrepareStatementCount());
            
            // Transaction metrics
            metrics.put("transaction_count", stats.getTransactionCount());
            metrics.put("successful_transaction_count", stats.getSuccessfulTransactionCount());
            
            // Database operation timing
            if (stats.getQueryExecutionCount() > 0) {
                double avgQueryTime = (double) stats.getQueryExecutionMaxTime() / stats.getQueryExecutionCount();
                metrics.put("average_query_execution_time", Math.round(avgQueryTime * 100.0) / 100.0);
            }
            
            metrics.put("statistics_enabled", true);
            metrics.put("start_time", stats.getStartTime());
            
        } catch (Exception e) {
            logger.error("Error retrieving performance metrics: {}", e.getMessage(), e);
            metrics.put("error", "Failed to retrieve metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    /**
     * Reset Hibernate statistics.
     */
    public void resetStatistics() {
        try {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            Statistics stats = sessionFactory.getStatistics();
            stats.clear();
            logger.info("Hibernate statistics reset successfully");
        } catch (Exception e) {
            logger.error("Error resetting statistics: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Log current performance metrics.
     */
    public void logPerformanceMetrics() {
        Map<String, Object> metrics = getPerformanceMetrics();
        
        if (Boolean.TRUE.equals(metrics.get("statistics_enabled"))) {
            logger.info("=== Database Performance Metrics ===");
            logger.info("Query Execution Count: {}", metrics.get("query_execution_count"));
            logger.info("Max Query Time: {}ms", metrics.get("query_execution_max_time"));
            logger.info("Average Query Time: {}ms", metrics.get("average_query_execution_time"));
            logger.info("Cache Hit Ratio: {}%", (Double) metrics.get("cache_hit_ratio") * 100);
            logger.info("Entity Operations - Load: {}, Insert: {}, Update: {}, Delete: {}", 
                metrics.get("entity_load_count"), metrics.get("entity_insert_count"), 
                metrics.get("entity_update_count"), metrics.get("entity_delete_count"));
            
            // Warn about slow queries
            Long maxQueryTime = (Long) metrics.get("query_execution_max_time");
            if (maxQueryTime != null && maxQueryTime > 1000) {
                logger.warn("Slow query detected! Max execution time: {}ms for query: {}", 
                    maxQueryTime, metrics.get("query_execution_max_time_query"));
            }
        } else {
            logger.warn("Hibernate statistics are not enabled. Performance monitoring is limited.");
        }
    }
    
    /**
     * Check for performance issues and return status information.
     */
    public Map<String, Object> checkPerformanceHealth() {
        Map<String, Object> healthStatus = new HashMap<>();
        
        try {
            Map<String, Object> metrics = getPerformanceMetrics();
            
            if (!Boolean.TRUE.equals(metrics.get("statistics_enabled"))) {
                healthStatus.put("status", "UP");
                healthStatus.put("message", "Statistics disabled - Hibernate statistics are not enabled");
                return healthStatus;
            }
            
            Long maxQueryTime = (Long) metrics.get("query_execution_max_time");
            Double cacheHitRatio = (Double) metrics.get("cache_hit_ratio");
            
            // Check for performance issues
            boolean hasSlowQueries = maxQueryTime != null && maxQueryTime > 5000; // 5 second threshold for health
            boolean hasLowCacheHitRatio = cacheHitRatio != null && cacheHitRatio < 0.7; // 70% threshold
            
            if (hasSlowQueries || hasLowCacheHitRatio) {
                healthStatus.put("status", "DOWN");
                
                if (hasSlowQueries) {
                    healthStatus.put("slow_queries", "Max query time: " + maxQueryTime + "ms");
                }
                if (hasLowCacheHitRatio) {
                    healthStatus.put("cache_performance", "Cache hit ratio: " + (cacheHitRatio * 100) + "%");
                }
                
                healthStatus.put("query_count", metrics.get("query_execution_count"));
                healthStatus.put("average_query_time", metrics.get("average_query_execution_time"));
                
                return healthStatus;
            }
            
            healthStatus.put("status", "UP");
            healthStatus.put("query_count", metrics.get("query_execution_count"));
            healthStatus.put("max_query_time", maxQueryTime);
            healthStatus.put("cache_hit_ratio", cacheHitRatio);
            healthStatus.put("average_query_time", metrics.get("average_query_execution_time"));
                
        } catch (Exception e) {
            healthStatus.put("status", "DOWN");
            healthStatus.put("error", e.getMessage());
        }
        
        return healthStatus;
    }
} 