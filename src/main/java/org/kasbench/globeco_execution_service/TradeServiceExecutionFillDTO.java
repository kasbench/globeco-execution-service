package org.kasbench.globeco_execution_service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO for sending execution fill data to the trade service.
 */
public class TradeServiceExecutionFillDTO {
    /**
     * The execution status (e.g., "PART", "FULL").
     */
    private String executionStatus;
    
    /**
     * The quantity filled for this execution.
     */
    private BigDecimal quantityFilled;
    
    /**
     * The version number for optimistic locking.
     */
    private Integer version;

    public TradeServiceExecutionFillDTO() {}

    public TradeServiceExecutionFillDTO(String executionStatus, BigDecimal quantityFilled, Integer version) {
        this.executionStatus = executionStatus;
        this.quantityFilled = quantityFilled;
        this.version = version;
    }

    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public BigDecimal getQuantityFilled() { return quantityFilled; }
    public void setQuantityFilled(BigDecimal quantityFilled) { this.quantityFilled = quantityFilled; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeServiceExecutionFillDTO that = (TradeServiceExecutionFillDTO) o;
        return Objects.equals(executionStatus, that.executionStatus) &&
                Objects.equals(quantityFilled, that.quantityFilled) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionStatus, quantityFilled, version);
    }
} 