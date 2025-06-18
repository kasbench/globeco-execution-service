package org.kasbench.globeco_execution_service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for managing executions.
 */
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {
    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Get executions with optional filtering, sorting, and pagination.
     * @param offset Number of records to skip (default: 0)
     * @param limit Maximum records to return (default: 50, max: 100)
     * @param executionStatus Filter by execution status
     * @param tradeType Filter by trade type
     * @param destination Filter by destination
     * @param securityId Filter by security ID
     * @param sortBy Comma-separated sort fields with optional minus prefix for descending (default: "id")
     * @return ExecutionPageDTO containing the filtered and paginated results
     */
    @GetMapping("/executions")
    public ExecutionPageDTO getAllExecutions(
            @RequestParam(value = "offset", defaultValue = "0") @Min(0) Integer offset,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) @Max(100) Integer limit,
            @RequestParam(value = "executionStatus", required = false) String executionStatus,
            @RequestParam(value = "tradeType", required = false) String tradeType,
            @RequestParam(value = "destination", required = false) String destination,
            @RequestParam(value = "securityId", required = false) String securityId,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy) {
        
        ExecutionQueryParams queryParams = new ExecutionQueryParams(
            offset, limit, executionStatus, tradeType, destination, securityId, sortBy
        );
        
        return executionService.findExecutions(queryParams);
    }

    /**
     * Get an execution by ID.
     * @param id The execution ID
     * @return The execution if found, 404 if not found
     */
    @GetMapping("/execution/{id}")
    public ResponseEntity<ExecutionDTO> getExecutionById(@PathVariable("id") Integer id) {
        // Use a query to get the single execution with security info
        ExecutionQueryParams queryParams = new ExecutionQueryParams(id);
        ExecutionPageDTO result = executionService.findExecutions(queryParams);
        
        if (result.getContent().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(result.getContent().get(0));
    }

    /**
     * Create a new execution.
     * @param postDTO The execution data
     * @return The created execution with 201 status
     */
    @PostMapping("/executions")
    public ResponseEntity<ExecutionDTO> createExecution(@RequestBody ExecutionPostDTO postDTO) {
        ExecutionDTO dto = executionService.createAndSendExecution(postDTO);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    /**
     * Update an execution's fill quantities and prices.
     * @param id The execution ID
     * @param putDTO The update data including quantityFilled, averagePrice, and version
     * @return The updated execution if found, 404 if not found
     */
    @PutMapping("/execution/{id}")
    public ResponseEntity<ExecutionDTO> updateExecution(@PathVariable("id") Integer id, @RequestBody ExecutionPutDTO putDTO) {
        Optional<Execution> updated = executionService.updateExecution(id, putDTO);
        if (updated.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        // Get the updated execution with security info using our enhanced query
        ExecutionQueryParams queryParams = new ExecutionQueryParams(id);
        ExecutionPageDTO result = executionService.findExecutions(queryParams);
        
        if (result.getContent().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(result.getContent().get(0));
    }

} 