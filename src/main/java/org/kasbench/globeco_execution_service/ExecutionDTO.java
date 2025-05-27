package org.kasbench.globeco_execution_service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * DTO for returning execution data.
 */
public class ExecutionDTO {
    /**
     * The unique execution ID.
     */
    private Integer id;
    
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
     * The quantity to trade.
     */
    private BigDecimal quantity;
    
    /**
     * The limit price for the trade (nullable).
     */
    private BigDecimal limitPrice;
    
    /**
     * When the execution was received.
     */
    private OffsetDateTime receivedTimestamp;
    
    /**
     * When the execution was sent to Kafka (nullable).
     */
    private OffsetDateTime sentTimestamp;
    
    /**
     * The trade service execution ID (nullable).
     */
    private Integer tradeServiceExecutionId;
    
    /**
     * The amount of the order that has been filled.
     */
    private BigDecimal quantityFilled;
    
    /**
     * The average price for filled quantities (nullable).
     */
    private BigDecimal averagePrice;
    
    /**
     * Optimistic locking version number.
     */
    private Integer version;

    public ExecutionDTO() {}

    public ExecutionDTO(Integer id, String executionStatus, String tradeType, String destination, String securityId, BigDecimal quantity, BigDecimal limitPrice, OffsetDateTime receivedTimestamp, OffsetDateTime sentTimestamp, Integer tradeServiceExecutionId, BigDecimal quantityFilled, BigDecimal averagePrice, Integer version) {
        this.id = id;
        this.executionStatus = executionStatus;
        this.tradeType = tradeType;
        this.destination = destination;
        this.securityId = securityId;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.receivedTimestamp = receivedTimestamp;
        this.sentTimestamp = sentTimestamp;
        this.tradeServiceExecutionId = tradeServiceExecutionId;
        this.quantityFilled = quantityFilled;
        this.averagePrice = averagePrice;
        this.version = version;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
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
    public OffsetDateTime getReceivedTimestamp() { return receivedTimestamp; }
    public void setReceivedTimestamp(OffsetDateTime receivedTimestamp) { this.receivedTimestamp = receivedTimestamp; }
    public OffsetDateTime getSentTimestamp() { return sentTimestamp; }
    public void setSentTimestamp(OffsetDateTime sentTimestamp) { this.sentTimestamp = sentTimestamp; }
    public Integer getTradeServiceExecutionId() { return tradeServiceExecutionId; }
    public void setTradeServiceExecutionId(Integer tradeServiceExecutionId) { this.tradeServiceExecutionId = tradeServiceExecutionId; }
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
        ExecutionDTO that = (ExecutionDTO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(executionStatus, that.executionStatus) &&
                Objects.equals(tradeType, that.tradeType) &&
                Objects.equals(destination, that.destination) &&
                Objects.equals(securityId, that.securityId) &&
                Objects.equals(quantity, that.quantity) &&
                Objects.equals(limitPrice, that.limitPrice) &&
                Objects.equals(receivedTimestamp, that.receivedTimestamp) &&
                Objects.equals(sentTimestamp, that.sentTimestamp) &&
                Objects.equals(tradeServiceExecutionId, that.tradeServiceExecutionId) &&
                Objects.equals(quantityFilled, that.quantityFilled) &&
                Objects.equals(averagePrice, that.averagePrice) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, executionStatus, tradeType, destination, securityId, quantity, limitPrice, receivedTimestamp, sentTimestamp, tradeServiceExecutionId, quantityFilled, averagePrice, version);
    }
} 