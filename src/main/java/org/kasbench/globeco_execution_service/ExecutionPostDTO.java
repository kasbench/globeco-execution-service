package org.kasbench.globeco_execution_service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO for creating new executions.
 */
public class ExecutionPostDTO {
    /**
     * The execution status (e.g., "NEW", "PART", "FULL").
     */
    private String executionStatus;
    
    /**
     * The trade type (e.g., "BUY", "SELL").
     */
    private String tradeType;
    
    /**
     * The destination exchange (e.g., "NYSE", "NASDAQ").
     */
    private String destination;
    
    /**
     * The security identifier (24 characters).
     */
    private String securityId;
    
    /**
     * The quantity to trade (not null).
     */
    private BigDecimal quantity;
    
    /**
     * The limit price for the trade (nullable).
     */
    private BigDecimal limitPrice;
    
    /**
     * The trade service execution ID (nullable).
     */
    private Integer tradeServiceExecutionId;
    
    /**
     * Optimistic locking version number (not null).
     */
    private Integer version;

    public ExecutionPostDTO() {}

    public ExecutionPostDTO(String executionStatus, String tradeType, String destination, String securityId, BigDecimal quantity, BigDecimal limitPrice, Integer tradeServiceExecutionId,  Integer version) {
        this.executionStatus = executionStatus;
        this.tradeType = tradeType;
        this.destination = destination;
        this.securityId = securityId;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.tradeServiceExecutionId = tradeServiceExecutionId;
        this.version = version;
    }

    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public String getTradeType() { return tradeType; }
    public void setTradeType(String tradeType) { this.tradeType = tradeType; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getSecurityId() { return securityId; }
    public void setSecurityId(String securityId) { this.securityId = securityId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
    public Integer getTradeServiceExecutionId() { return tradeServiceExecutionId; }
    public void setTradeServiceExecutionId(Integer tradeServiceExecutionId) { this.tradeServiceExecutionId = tradeServiceExecutionId; } 
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionPostDTO that = (ExecutionPostDTO) o;
        return Objects.equals(executionStatus, that.executionStatus) &&
                Objects.equals(tradeType, that.tradeType) &&
                Objects.equals(destination, that.destination) &&
                Objects.equals(securityId, that.securityId) &&
                Objects.equals(quantity, that.quantity) &&
                Objects.equals(limitPrice, that.limitPrice) &&
                Objects.equals(tradeServiceExecutionId, that.tradeServiceExecutionId) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionStatus, tradeType, destination, securityId, quantity, limitPrice, tradeServiceExecutionId, version);
    }
} 