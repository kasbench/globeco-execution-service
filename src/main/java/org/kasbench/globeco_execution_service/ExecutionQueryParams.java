package org.kasbench.globeco_execution_service;

import java.util.Objects;

/**
 * Query parameters for filtering and pagination of executions.
 */
public class ExecutionQueryParams {
    
    // Pagination parameters
    private Integer offset = 0;
    private Integer limit = 50;
    
    // Filtering parameters
    private Integer id;
    private String executionStatus;
    private String tradeType;
    private String destination;
    private String securityId;
    
    // Sorting parameters
    private String sortBy = "id";
    
    public ExecutionQueryParams() {}
    
    public ExecutionQueryParams(Integer offset, Integer limit, String executionStatus, 
                               String tradeType, String destination, String securityId, String sortBy) {
        this.offset = offset != null ? offset : 0;
        this.limit = limit != null && limit <= 100 ? limit : 50; // Max 100 per requirements
        this.executionStatus = executionStatus;
        this.tradeType = tradeType;
        this.destination = destination;
        this.securityId = securityId;
        this.sortBy = sortBy != null ? sortBy : "id";
    }
    
    public ExecutionQueryParams(Integer id) {
        this.id = id;
        this.offset = 0;
        this.limit = 1;
        this.sortBy = "id";
    }
    
    public Integer getOffset() { 
        return offset; 
    }
    
    public void setOffset(Integer offset) { 
        this.offset = offset != null ? offset : 0; 
    }
    
    public Integer getLimit() { 
        return limit; 
    }
    
    public void setLimit(Integer limit) { 
        this.limit = limit != null && limit <= 100 ? limit : 50; 
    }
    
    public Integer getId() { 
        return id; 
    }
    
    public void setId(Integer id) { 
        this.id = id; 
    }
    
    public String getExecutionStatus() { 
        return executionStatus; 
    }
    
    public void setExecutionStatus(String executionStatus) { 
        this.executionStatus = executionStatus; 
    }
    
    public String getTradeType() { 
        return tradeType; 
    }
    
    public void setTradeType(String tradeType) { 
        this.tradeType = tradeType; 
    }
    
    public String getDestination() { 
        return destination; 
    }
    
    public void setDestination(String destination) { 
        this.destination = destination; 
    }
    
    public String getSecurityId() { 
        return securityId; 
    }
    
    public void setSecurityId(String securityId) { 
        this.securityId = securityId; 
    }
    
    public String getSortBy() { 
        return sortBy; 
    }
    
    public void setSortBy(String sortBy) { 
        this.sortBy = sortBy != null ? sortBy : "id"; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionQueryParams that = (ExecutionQueryParams) o;
        return Objects.equals(offset, that.offset) &&
                Objects.equals(limit, that.limit) &&
                Objects.equals(id, that.id) &&
                Objects.equals(executionStatus, that.executionStatus) &&
                Objects.equals(tradeType, that.tradeType) &&
                Objects.equals(destination, that.destination) &&
                Objects.equals(securityId, that.securityId) &&
                Objects.equals(sortBy, that.sortBy);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(offset, limit, executionStatus, tradeType, destination, securityId, sortBy);
    }
    
    @Override
    public String toString() {
        return "ExecutionQueryParams{" +
                "offset=" + offset +
                ", limit=" + limit +
                ", id=" + id +
                ", executionStatus='" + executionStatus + '\'' +
                ", tradeType='" + tradeType + '\'' +
                ", destination='" + destination + '\'' +
                ", securityId='" + securityId + '\'' +
                ", sortBy='" + sortBy + '\'' +
                '}';
    }
} 