package org.kasbench.globeco_execution_service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Optional;

/**
 * REST controller for managing executions.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Executions", description = "Operations for managing trade executions")
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
    @Operation(
        summary = "Get executions with filtering and pagination",
        description = "Retrieve executions with optional filtering by status, trade type, destination, or security ID. " +
                     "Supports pagination and multi-field sorting. Returns enriched data with security ticker information."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved executions",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionPageDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/executions")
    public ExecutionPageDTO getAllExecutions(
            @Parameter(description = "Number of records to skip", example = "0")
            @RequestParam(value = "offset", defaultValue = "0") @Min(0) Integer offset,
            @Parameter(description = "Maximum records to return (max 100)", example = "50")
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) @Max(100) Integer limit,
            @Parameter(description = "Filter by execution status", example = "NEW")
            @RequestParam(value = "executionStatus", required = false) String executionStatus,
            @Parameter(description = "Filter by trade type", example = "BUY")
            @RequestParam(value = "tradeType", required = false) String tradeType,
            @Parameter(description = "Filter by destination exchange", example = "NYSE")
            @RequestParam(value = "destination", required = false) String destination,
            @Parameter(description = "Filter by security identifier", example = "SEC001")
            @RequestParam(value = "securityId", required = false) String securityId,
            @Parameter(description = "Comma-separated sort fields with minus prefix for descending", example = "receivedTimestamp,-id")
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
    @Operation(
        summary = "Get execution by ID",
        description = "Retrieve a specific execution by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Execution found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDTO.class))),
        @ApiResponse(responseCode = "404", description = "Execution not found")
    })
    @GetMapping("/execution/{id}")
    public ResponseEntity<ExecutionDTO> getExecutionById(
            @Parameter(description = "Execution ID", example = "1")
            @PathVariable("id") Integer id) {
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
    @Operation(
        summary = "Create a new execution",
        description = "Create a single trade execution and send it to the trading platform"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Execution created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid execution data")
    })
    @PostMapping("/executions")
    public ResponseEntity<ExecutionDTO> createExecution(
            @Parameter(description = "Execution data", required = true)
            @RequestBody ExecutionPostDTO postDTO) {
        ExecutionDTO dto = executionService.createAndSendExecution(postDTO);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }
    
    /**
     * Create multiple executions in a batch operation.
     * @param batchRequest The batch request containing up to 100 executions
     * @return BatchExecutionResponseDTO with results for each execution
     */
    @Operation(
        summary = "Create multiple executions in batch",
        description = "Process up to 100 executions in a single batch operation. " +
                     "Returns detailed results for each execution including success/failure status."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "All executions created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BatchExecutionResponseDTO.class))),
        @ApiResponse(responseCode = "207", description = "Partial success - some executions failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BatchExecutionResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "All executions failed or validation errors",
                    content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Batch Operations")
    @PostMapping("/executions/batch")
    public ResponseEntity<BatchExecutionResponseDTO> createBatchExecutions(
            @Parameter(description = "Batch request with up to 100 executions", required = true)
            @Valid @RequestBody BatchExecutionRequestDTO batchRequest) {
        BatchExecutionResponseDTO response = executionService.createBatchExecutions(batchRequest);
        
        // Determine response status based on batch operation result
        HttpStatus status;
        switch (response.getStatus()) {
            case "SUCCESS":
                status = HttpStatus.CREATED;
                break;
            case "PARTIAL_SUCCESS":
                status = HttpStatus.MULTI_STATUS; // 207
                break;
            case "FAILED":
            default:
                status = HttpStatus.BAD_REQUEST;
                break;
        }
        
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Update an execution's fill quantities and prices.
     * @param id The execution ID
     * @param putDTO The update data including quantityFilled, averagePrice, and version
     * @return The updated execution if found, 404 if not found
     */
    @Operation(
        summary = "Update execution fill data",
        description = "Update an execution's fill quantities and average price. Requires version for optimistic locking."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Execution updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDTO.class))),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "409", description = "Version conflict - execution was modified by another process")
    })
    @PutMapping("/execution/{id}")
    public ResponseEntity<ExecutionDTO> updateExecution(
            @Parameter(description = "Execution ID", example = "1")
            @PathVariable("id") Integer id, 
            @Parameter(description = "Update data with fill information", required = true)
            @RequestBody ExecutionPutDTO putDTO) {
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