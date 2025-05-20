package org.kasbench.globeco_execution_service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public class ExecutionDTO {
    private Integer id;
    private String executionStatus;
    private String tradeType;
    private String destination;
    private BigDecimal quantity;
    private BigDecimal limitPrice;
    private OffsetDateTime receivedTimestamp;
    private OffsetDateTime sentTimestamp;
    private Integer version;

    public ExecutionDTO() {}

    public ExecutionDTO(Integer id, String executionStatus, String tradeType, String destination, BigDecimal quantity, BigDecimal limitPrice, OffsetDateTime receivedTimestamp, OffsetDateTime sentTimestamp, Integer version) {
        this.id = id;
        this.executionStatus = executionStatus;
        this.tradeType = tradeType;
        this.destination = destination;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.receivedTimestamp = receivedTimestamp;
        this.sentTimestamp = sentTimestamp;
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
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
    public OffsetDateTime getReceivedTimestamp() { return receivedTimestamp; }
    public void setReceivedTimestamp(OffsetDateTime receivedTimestamp) { this.receivedTimestamp = receivedTimestamp; }
    public OffsetDateTime getSentTimestamp() { return sentTimestamp; }
    public void setSentTimestamp(OffsetDateTime sentTimestamp) { this.sentTimestamp = sentTimestamp; }
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
                Objects.equals(quantity, that.quantity) &&
                Objects.equals(limitPrice, that.limitPrice) &&
                Objects.equals(receivedTimestamp, that.receivedTimestamp) &&
                Objects.equals(sentTimestamp, that.sentTimestamp) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, executionStatus, tradeType, destination, quantity, limitPrice, receivedTimestamp, sentTimestamp, version);
    }
} 