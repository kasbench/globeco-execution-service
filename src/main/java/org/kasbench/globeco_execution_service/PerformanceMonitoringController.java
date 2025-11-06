package org.kasbench.globeco_execution_service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for performance monitoring and diagnostics.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "Performance Monitoring", description = "Endpoints for performance diagnostics")
public class PerformanceMonitoringController {
    
    private final SecurityServiceClientImpl securityServiceClient;
    private final ExecutionServiceImpl executionService;
    
    public PerformanceMonitoringController(SecurityServiceClientImpl securityServiceClient, 
                                         ExecutionServiceImpl executionService) {
        this.securityServiceClient = securityServiceClient;
        this.executionService = executionService;
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
    
    /**
     * Diagnostic test for bulk update functionality.
     */
    @Operation(
        summary = "Test bulk update functionality",
        description = "Runs diagnostic tests on bulk update operations to help troubleshoot issues"
    )
    @PostMapping("/diagnostic-bulk-update")
    public Map<String, String> diagnosticBulkUpdate(@RequestBody List<Integer> executionIds) {
        try {
            executionService.diagnosticBulkUpdateTest(executionIds);
            return Map.of(
                "status", "success", 
                "message", "Diagnostic test completed successfully. Check logs for detailed results."
            );
        } catch (Exception e) {
            return Map.of(
                "status", "error", 
                "message", "Diagnostic test failed: " + e.getMessage(),
                "recommendation", "Check application logs for detailed error information"
            );
        }
    }
}