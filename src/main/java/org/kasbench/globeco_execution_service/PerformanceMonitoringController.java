package org.kasbench.globeco_execution_service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for performance monitoring and diagnostics.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "Performance Monitoring", description = "Endpoints for performance diagnostics")
public class PerformanceMonitoringController {
    
    private final SecurityServiceClientImpl securityServiceClient;
    
    public PerformanceMonitoringController(SecurityServiceClientImpl securityServiceClient) {
        this.securityServiceClient = securityServiceClient;
    }
    
    /**
     * Get security service cache statistics.
     */
    @Operation(
        summary = "Get security service cache statistics",
        description = "Returns cache hit rates and performance metrics for the security service client"
    )
    @GetMapping("/security-cache-stats")
    public Map<String, Object> getSecurityCacheStats() {
        return securityServiceClient.getCacheStats();
    }
    
    /**
     * Get database connection pool statistics.
     */
    @Operation(
        summary = "Get database connection pool statistics",
        description = "Returns connection pool metrics for database performance monitoring"
    )
    @GetMapping("/db-pool-stats")
    public Map<String, Object> getDatabasePoolStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Note: In a real implementation, you would inject HikariDataSource
        // and get actual pool metrics. For now, we'll return placeholder info.
        stats.put("note", "Database pool metrics would be available here");
        stats.put("recommendation", "Monitor HikariCP metrics via JMX or actuator endpoints");
        
        return stats;
    }
    
    /**
     * Clear security service cache (for testing/debugging).
     */
    @Operation(
        summary = "Clear security service cache",
        description = "Clears the security service cache - use for testing or when cache becomes stale"
    )
    @GetMapping("/clear-security-cache")
    public Map<String, String> clearSecurityCache() {
        securityServiceClient.clearCache();
        return Map.of("status", "Cache cleared successfully");
    }
}