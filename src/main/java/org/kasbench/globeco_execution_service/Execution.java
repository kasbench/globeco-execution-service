package org.kasbench.globeco_execution_service;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "execution")
public class Execution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "execution_status", nullable = false, length = 20)
    private String executionStatus;

    @Column(name = "trade_type", nullable = false, length = 10)
    private String tradeType;

    @Column(name = "destination", nullable = false, length = 20)
    private String destination;

    @Column(name = "security_id", nullable = false, length = 24)
    private String securityId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 18, scale = 8)
    private BigDecimal limitPrice;

    @Column(name = "received_timestamp", nullable = false)
    private OffsetDateTime receivedTimestamp;

    @Column(name = "sent_timestamp")
    private OffsetDateTime sentTimestamp;

    @Column(name = "trade_service_execution_id")
    private Integer tradeServiceExecutionId;

    @Column(name = "quantity_filled", precision = 18, scale = 8)
    private BigDecimal quantityFilled;

    @Column(name = "average_price", precision = 18, scale = 8)
    private BigDecimal averagePrice;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public Execution() {}

    public Execution(Integer id, String executionStatus, String tradeType, String destination, String securityId, BigDecimal quantity, BigDecimal limitPrice, OffsetDateTime receivedTimestamp, OffsetDateTime sentTimestamp, Integer tradeServiceExecutionId, BigDecimal quantityFilled, BigDecimal averagePrice, Integer version) {
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
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public BigDecimal getQuantityFilled() { return quantityFilled; }
    public void setQuantityFilled(BigDecimal quantityFilled) { this.quantityFilled = quantityFilled; }
    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Execution that = (Execution) o;
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