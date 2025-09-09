package org.kasbench.globeco_execution_service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom implementation of ExecutionRepository for bulk operations.
 * Uses JPA batch processing for optimal performance in bulk insert scenarios.
 */
@Repository
public class ExecutionRepositoryImpl implements ExecutionRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public List<Execution> bulkInsert(List<Execution> executions) {
        if (executions == null || executions.isEmpty()) {
            return new ArrayList<>();
        }

        List<Execution> insertedExecutions = new ArrayList<>();
        
        // Use JPA batch processing for better compatibility
        int batchSize = 50; // Configurable batch size for optimal performance
        
        for (int i = 0; i < executions.size(); i++) {
            Execution execution = executions.get(i);
            
            // Ensure version is set for new entities
            if (execution.getVersion() == null) {
                execution.setVersion(0);
            }
            
            entityManager.persist(execution);
            insertedExecutions.add(execution);
            
            // Flush and clear every batch to avoid memory issues
            if (i % batchSize == 0 || i == executions.size() - 1) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        
        return insertedExecutions;
    }
}