package org.kasbench.globeco_execution_service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO for updating execution quantityFilled and averagePrice.
 */
public class ExecutionPutDTO {
    /**
     * Amount to increment quantity_filled in the database (not null).
     */
    private BigDecimal quantityFilled;

    /**
     * Value to set as average_price in the database (nullable).
     */
    private BigDecimal averagePrice;

    /**
     * Optimistic locking version number (not null).
     */
    private Integer version;

    public ExecutionPutDTO() {}

    public ExecutionPutDTO(BigDecimal quantityFilled, BigDecimal averagePrice, Integer version) {
        this.quantityFilled = quantityFilled;
        this.averagePrice = averagePrice;
        this.version = version;
    }

    public BigDecimal getQuantityFilled() { return quantityFilled; }
    public void setQuantityFilled(BigDecimal quantityFilled) { this.quantityFilled = quantityFilled; }
    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionPutDTO that = (ExecutionPutDTO) o;
        return Objects.equals(quantityFilled, that.quantityFilled) &&
                Objects.equals(averagePrice, that.averagePrice) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quantityFilled, averagePrice, version);
    }
} 