package org.kasbench.globeco_execution_service;

import java.util.List;

/**
 * Custom repository interface for bulk operations that require native SQL implementation.
 */
public interface ExecutionRepositoryCustom {
    
    /**
     * Bulk insert executions using native SQL for optimal performance.
     * This method bypasses JPA's individual insert operations and uses
     * a single batch insert statement for better performance.
     * 
     * @param executions List of executions to insert
     * @return List of inserted executions with generated IDs
     */
    List<Execution> bulkInsert(List<Execution> executions);
}