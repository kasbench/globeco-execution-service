package org.kasbench.globeco_execution_service;

import java.util.List;
import java.util.Objects;

/**
 * DTO for paginated execution results.
 */
public class ExecutionPageDTO {
    /**
     * The list of executions for this page.
     */
    private List<ExecutionDTO> content;
    
    /**
     * Pagination metadata.
     */
    private PaginationDTO pagination;
    
    public ExecutionPageDTO() {}
    
    public ExecutionPageDTO(List<ExecutionDTO> content, PaginationDTO pagination) {
        this.content = content;
        this.pagination = pagination;
    }
    
    public List<ExecutionDTO> getContent() { 
        return content; 
    }
    
    public void setContent(List<ExecutionDTO> content) { 
        this.content = content; 
    }
    
    public PaginationDTO getPagination() { 
        return pagination; 
    }
    
    public void setPagination(PaginationDTO pagination) { 
        this.pagination = pagination; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionPageDTO that = (ExecutionPageDTO) o;
        return Objects.equals(content, that.content) &&
                Objects.equals(pagination, that.pagination);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(content, pagination);
    }
    
    @Override
    public String toString() {
        return "ExecutionPageDTO{" +
                "content=" + content +
                ", pagination=" + pagination +
                '}';
    }
} 