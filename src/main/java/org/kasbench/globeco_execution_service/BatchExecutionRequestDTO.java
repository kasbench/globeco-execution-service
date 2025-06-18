package org.kasbench.globeco_execution_service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * DTO for batch execution requests.
 */
public class BatchExecutionRequestDTO {
    
    /**
     * List of executions to create (maximum 100).
     */
    @NotNull(message = "Executions list cannot be null")
    @NotEmpty(message = "Executions list cannot be empty")
    @Size(max = 100, message = "Maximum 100 executions allowed per batch")
    @Valid
    private List<ExecutionPostDTO> executions;
    
    public BatchExecutionRequestDTO() {}
    
    public BatchExecutionRequestDTO(List<ExecutionPostDTO> executions) {
        this.executions = executions;
    }
    
    public List<ExecutionPostDTO> getExecutions() { 
        return executions; 
    }
    
    public void setExecutions(List<ExecutionPostDTO> executions) { 
        this.executions = executions; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchExecutionRequestDTO that = (BatchExecutionRequestDTO) o;
        return Objects.equals(executions, that.executions);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(executions);
    }
    
    @Override
    public String toString() {
        return "BatchExecutionRequestDTO{" +
                "executions=" + executions +
                '}';
    }
} 