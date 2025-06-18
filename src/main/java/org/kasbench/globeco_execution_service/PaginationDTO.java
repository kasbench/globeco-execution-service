package org.kasbench.globeco_execution_service;

import java.util.Objects;

/**
 * DTO for pagination metadata.
 */
public class PaginationDTO {
    /**
     * Number of records to skip.
     */
    private Integer offset;
    
    /**
     * Maximum records to return.
     */
    private Integer limit;
    
    /**
     * Total number of elements across all pages.
     */
    private Long totalElements;
    
    /**
     * Total number of pages.
     */
    private Integer totalPages;
    
    /**
     * Current page number (0-based).
     */
    private Integer currentPage;
    
    /**
     * Whether there is a next page.
     */
    private Boolean hasNext;
    
    /**
     * Whether there is a previous page.
     */
    private Boolean hasPrevious;
    
    public PaginationDTO() {}
    
    public PaginationDTO(Integer offset, Integer limit, Long totalElements, Integer totalPages, 
                        Integer currentPage, Boolean hasNext, Boolean hasPrevious) {
        this.offset = offset;
        this.limit = limit;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.currentPage = currentPage;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }
    
    public Integer getOffset() { 
        return offset; 
    }
    
    public void setOffset(Integer offset) { 
        this.offset = offset; 
    }
    
    public Integer getLimit() { 
        return limit; 
    }
    
    public void setLimit(Integer limit) { 
        this.limit = limit; 
    }
    
    public Long getTotalElements() { 
        return totalElements; 
    }
    
    public void setTotalElements(Long totalElements) { 
        this.totalElements = totalElements; 
    }
    
    public Integer getTotalPages() { 
        return totalPages; 
    }
    
    public void setTotalPages(Integer totalPages) { 
        this.totalPages = totalPages; 
    }
    
    public Integer getCurrentPage() { 
        return currentPage; 
    }
    
    public void setCurrentPage(Integer currentPage) { 
        this.currentPage = currentPage; 
    }
    
    public Boolean getHasNext() { 
        return hasNext; 
    }
    
    public void setHasNext(Boolean hasNext) { 
        this.hasNext = hasNext; 
    }
    
    public Boolean getHasPrevious() { 
        return hasPrevious; 
    }
    
    public void setHasPrevious(Boolean hasPrevious) { 
        this.hasPrevious = hasPrevious; 
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaginationDTO that = (PaginationDTO) o;
        return Objects.equals(offset, that.offset) &&
                Objects.equals(limit, that.limit) &&
                Objects.equals(totalElements, that.totalElements) &&
                Objects.equals(totalPages, that.totalPages) &&
                Objects.equals(currentPage, that.currentPage) &&
                Objects.equals(hasNext, that.hasNext) &&
                Objects.equals(hasPrevious, that.hasPrevious);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(offset, limit, totalElements, totalPages, currentPage, hasNext, hasPrevious);
    }
    
    @Override
    public String toString() {
        return "PaginationDTO{" +
                "offset=" + offset +
                ", limit=" + limit +
                ", totalElements=" + totalElements +
                ", totalPages=" + totalPages +
                ", currentPage=" + currentPage +
                ", hasNext=" + hasNext +
                ", hasPrevious=" + hasPrevious +
                '}';
    }
} 