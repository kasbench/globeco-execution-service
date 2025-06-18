package org.kasbench.globeco_execution_service;

import java.util.List;
import java.util.Objects;

/**
 * DTO for batch execution responses.
 */
public class BatchExecutionResponseDTO {
    
    /**
     * Overall status of the batch operation ("SUCCESS", "PARTIAL_SUCCESS", "FAILED").
     */
    private String status;
    
    /**
     * Overall message describing the batch operation result.
     */
    private String message;
    
    /**
     * Total number of executions requested.
     */
    private Integer totalRequested;
    
    /**
     * Number of executions that were successfully created.
     */
    private Integer successful;
    
    /**
     * Number of executions that failed.
     */
    private Integer failed;
    
    /**
     * Detailed results for each execution in the batch.
     */
    private List<ExecutionResultDTO> results;
    
    public BatchExecutionResponseDTO() {}
    
    public BatchExecutionResponseDTO(String status, String message, Integer totalRequested, 
                                   Integer successful, Integer failed, List<ExecutionResultDTO> results) {
        this.status = status;
        this.message = message;
        this.totalRequested = totalRequested;
        this.successful = successful;
        this.failed = failed;
        this.results = results;
    }
    
    /**
     * Create a response based on the results.
     */
    public static BatchExecutionResponseDTO fromResults(List<ExecutionResultDTO> results) {
        int totalRequested = results.size();
        long successful = results.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        int failed = totalRequested - (int) successful;
        
        String status;
        String message;
        
        if (failed == 0) {
            status = "SUCCESS";
            message = "All executions created successfully";
        } else if (successful == 0) {
            status = "FAILED";
            message = "All executions failed";
        } else {
            status = "PARTIAL_SUCCESS";
            message = String.format("%d of %d executions created successfully", successful, totalRequested);
        }
        
        return new BatchExecutionResponseDTO(status, message, totalRequested, (int) successful, failed, results);
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
    
    public Integer getTotalRequested() { 
        return totalRequested; 
    }
    
    public void setTotalRequested(Integer totalRequested) { 
        this.totalRequested = totalRequested; 
    }
    
    public Integer getSuccessful() { 
        return successful; 
    }
    
    public void setSuccessful(Integer successful) { 
        this.successful = successful; 
    }
    
    public Integer getFailed() { 
        return failed; 
    }
    
    public void setFailed(Integer failed) { 
        this.failed = failed; 
    }
    
    public List<ExecutionResultDTO> getResults() { 
        return results; 
    }
    
    public void setResults(List<ExecutionResultDTO> results) { 
        this.results = results; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchExecutionResponseDTO that = (BatchExecutionResponseDTO) o;
        return Objects.equals(status, that.status) &&
                Objects.equals(message, that.message) &&
                Objects.equals(totalRequested, that.totalRequested) &&
                Objects.equals(successful, that.successful) &&
                Objects.equals(failed, that.failed) &&
                Objects.equals(results, that.results);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(status, message, totalRequested, successful, failed, results);
    }
    
    @Override
    public String toString() {
        return "BatchExecutionResponseDTO{" +
                "status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", totalRequested=" + totalRequested +
                ", successful=" + successful +
                ", failed=" + failed +
                ", results=" + results +
                '}';
    }
} 