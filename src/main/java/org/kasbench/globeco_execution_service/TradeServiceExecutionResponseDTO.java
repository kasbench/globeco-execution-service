package org.kasbench.globeco_execution_service;

import java.util.Objects;

/**
 * DTO for receiving execution data from the trade service GET response.
 * This is a simplified version that only includes the fields we need.
 */
public class TradeServiceExecutionResponseDTO {
    /**
     * The execution ID.
     */
    private Integer id;
    
    /**
     * The version number for optimistic locking.
     */
    private Integer version;

    public TradeServiceExecutionResponseDTO() {}

    public TradeServiceExecutionResponseDTO(Integer id, Integer version) {
        this.id = id;
        this.version = version;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeServiceExecutionResponseDTO that = (TradeServiceExecutionResponseDTO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
} 