package org.kasbench.globeco_execution_service;

import java.util.Objects;

/**
 * DTO for security information including ID and ticker.
 */
public class SecurityDTO {
    /**
     * The security identifier (24 characters).
     */
    private String securityId;
    
    /**
     * The security ticker symbol.
     */
    private String ticker;
    
    public SecurityDTO() {}
    
    public SecurityDTO(String securityId, String ticker) {
        this.securityId = securityId;
        this.ticker = ticker;
    }
    
    public String getSecurityId() { 
        return securityId; 
    }
    
    public void setSecurityId(String securityId) { 
        this.securityId = securityId; 
    }
    
    public String getTicker() { 
        return ticker; 
    }
    
    public void setTicker(String ticker) { 
        this.ticker = ticker; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecurityDTO that = (SecurityDTO) o;
        return Objects.equals(securityId, that.securityId) &&
                Objects.equals(ticker, that.ticker);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(securityId, ticker);
    }
    
    @Override
    public String toString() {
        return "SecurityDTO{" +
                "securityId='" + securityId + '\'' +
                ", ticker='" + ticker + '\'' +
                '}';
    }
} 