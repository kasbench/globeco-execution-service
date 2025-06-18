package org.kasbench.globeco_execution_service;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing executions.
 */
public interface ExecutionService {
    /**
     * Save an execution entity.
     * @param execution The execution to save
     * @return The saved execution
     */
    Execution save(Execution execution);
    
    /**
     * Find an execution by ID.
     * @param id The execution ID
     * @return Optional containing the execution if found
     */
    Optional<Execution> findById(Integer id);
    
    /**
     * Find all executions.
     * @return List of all executions
     */
    List<Execution> findAll();
    
    /**
     * Find executions with filtering, sorting, and pagination.
     * @param queryParams The query parameters for filtering and pagination
     * @return ExecutionPageDTO containing the filtered and paginated results
     */
    ExecutionPageDTO findExecutions(ExecutionQueryParams queryParams);
    
    /**
     * Delete an execution by ID with optimistic locking.
     * @param id The execution ID
     * @param version The expected version for optimistic locking
     */
    void deleteById(Integer id, Integer version);
    
    /**
     * Create a new execution and send it to Kafka.
     * @param postDTO The execution data
     * @return The created execution DTO
     */
    ExecutionDTO createAndSendExecution(ExecutionPostDTO postDTO);
    
    /**
     * Update an execution's fill quantities and prices.
     * @param id The execution ID
     * @param putDTO The update data
     * @return Optional containing the updated execution if found
     */
    Optional<Execution> updateExecution(Integer id, ExecutionPutDTO putDTO);
    
    /**
     * Create multiple executions in a batch operation.
     * @param batchRequest The batch request containing multiple executions
     * @return BatchExecutionResponseDTO containing results for each execution
     */
    BatchExecutionResponseDTO createBatchExecutions(BatchExecutionRequestDTO batchRequest);
} 