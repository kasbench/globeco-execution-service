package org.kasbench.globeco_execution_service;

import java.util.Objects;

/**
 * DTO for individual execution results within batch operations.
 */
public class ExecutionResultDTO {
    
    /**
     * Index of the execution in the original batch request.
     */
    private Integer requestIndex;
    
    /**
     * Status of this specific execution result ("SUCCESS", "FAILED").
     */
    private String status;
    
    /**
     * Error message if the execution failed.
     */
    private String message;
    
    /**
     * The created execution DTO (null if failed).
     */
    private ExecutionDTO execution;
    
    public ExecutionResultDTO() {}
    
    public ExecutionResultDTO(Integer requestIndex, String status, String message, ExecutionDTO execution) {
        this.requestIndex = requestIndex;
        this.status = status;
        this.message = message;
        this.execution = execution;
    }
    
    /**
     * Create a successful result.
     */
    public static ExecutionResultDTO success(Integer requestIndex, ExecutionDTO execution) {
        return new ExecutionResultDTO(requestIndex, "SUCCESS", null, execution);
    }
    
    /**
     * Create a failed result.
     */
    public static ExecutionResultDTO failure(Integer requestIndex, String message) {
        return new ExecutionResultDTO(requestIndex, "FAILED", message, null);
    }
    
    public Integer getRequestIndex() { 
        return requestIndex; 
    }
    
    public void setRequestIndex(Integer requestIndex) { 
        this.requestIndex = requestIndex; 
    }
    
    public String getStatus() { 
        return status; 
    }
    
    public void setStatus(String status) { 
        this.status = status; 
    }
    
    public String getMessage() { 
        return message; 
    }
    
    public void setMessage(String message) { 
        this.message = message; 
    }
    
    public ExecutionDTO getExecution() { 
        return execution; 
    }
    
    public void setExecution(ExecutionDTO execution) { 
        this.execution = execution; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionResultDTO that = (ExecutionResultDTO) o;
        return Objects.equals(requestIndex, that.requestIndex) &&
                Objects.equals(status, that.status) &&
                Objects.equals(message, that.message) &&
                Objects.equals(execution, that.execution);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(requestIndex, status, message, execution);
    }
    
    @Override
    public String toString() {
        return "ExecutionResultDTO{" +
                "requestIndex=" + requestIndex +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", execution=" + execution +
                '}';
    }
} 