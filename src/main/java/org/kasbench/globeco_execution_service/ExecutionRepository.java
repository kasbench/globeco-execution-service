package org.kasbench.globeco_execution_service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, Integer>, JpaSpecificationExecutor<Execution> {
    
    /**
     * Optimized query to find executions by status using index.
     * @param status The execution status to filter by
     * @return List of executions with the specified status
     */
    @Query("SELECT e FROM Execution e WHERE e.executionStatus = :status ORDER BY e.receivedTimestamp DESC")
    List<Execution> findByExecutionStatusOptimized(@Param("status") String status);
    
    /**
     * Optimized query to find executions by security ID with limit.
     * @param securityId The security ID to filter by
     * @param limit Maximum number of results to return
     * @return List of executions for the specified security
     */
    @Query(value = "SELECT * FROM execution WHERE security_id = :securityId ORDER BY received_timestamp DESC LIMIT :limit", 
           nativeQuery = true)
    List<Execution> findBySecurityIdWithLimit(@Param("securityId") String securityId, @Param("limit") int limit);
    
    /**
     * Optimized query to find executions within a time range using indexes.
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @return List of executions within the time range
     */
    @Query("SELECT e FROM Execution e WHERE e.receivedTimestamp BETWEEN :startTime AND :endTime ORDER BY e.receivedTimestamp DESC")
    List<Execution> findByTimeRangeOptimized(@Param("startTime") OffsetDateTime startTime, 
                                           @Param("endTime") OffsetDateTime endTime);
    
    /**
     * Bulk update operation for execution status.
     * @param ids List of execution IDs to update
     * @param newStatus New status to set
     * @return Number of updated records
     */
    @Modifying
    @Query("UPDATE Execution e SET e.executionStatus = :newStatus, e.version = e.version + 1 WHERE e.id IN :ids")
    int bulkUpdateStatus(@Param("ids") List<Integer> ids, @Param("newStatus") String newStatus);
    
    /**
     * Optimized count query for status.
     * @param status The execution status to count
     * @return Count of executions with the specified status
     */
    @Query("SELECT COUNT(e) FROM Execution e WHERE e.executionStatus = :status")
    long countByExecutionStatusOptimized(@Param("status") String status);
    
    /**
     * Find executions with unfilled quantities (useful for operational monitoring).
     * @return List of executions that are not fully filled
     */
    @Query("SELECT e FROM Execution e WHERE e.quantityFilled < e.quantity ORDER BY e.receivedTimestamp DESC")
    List<Execution> findUnfilledExecutions();
    
    /**
     * Find recent executions for a specific trade type with limit.
     * @param tradeType The trade type (BUY/SELL)
     * @param limit Maximum number of results
     * @return List of recent executions for the trade type
     */
    @Query(value = "SELECT * FROM execution WHERE trade_type = :tradeType ORDER BY received_timestamp DESC LIMIT :limit", 
           nativeQuery = true)
    List<Execution> findRecentByTradeType(@Param("tradeType") String tradeType, @Param("limit") int limit);
} 