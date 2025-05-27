package org.kasbench.globeco_execution_service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * Get all executions.
     * @return List of all executions
     */
    @GetMapping("/executions")
    public List<ExecutionDTO> getAllExecutions() {
        return executionService.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Get an execution by ID.
     * @param id The execution ID
     * @return The execution if found, 404 if not found
     */
    @GetMapping("/execution/{id}")
    public ResponseEntity<ExecutionDTO> getExecutionById(@PathVariable("id") Integer id) {
        Optional<Execution> execution = executionService.findById(id);
        return execution.map(value -> ResponseEntity.ok(toDTO(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
        return updated.map(value -> ResponseEntity.ok(toDTO(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ExecutionDTO toDTO(Execution execution) {
        return new ExecutionDTO(
                execution.getId(),
                execution.getExecutionStatus(),
                execution.getTradeType(),
                execution.getDestination(),
                execution.getSecurityId(),
                execution.getQuantity(),
                execution.getLimitPrice(),
                execution.getReceivedTimestamp(),
                execution.getSentTimestamp(),
                execution.getTradeServiceExecutionId(),
                execution.getQuantityFilled(),
                execution.getAveragePrice(),
                execution.getVersion()
        );
    }

} 