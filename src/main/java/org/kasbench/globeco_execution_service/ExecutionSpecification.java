package org.kasbench.globeco_execution_service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for dynamic Execution queries with performance optimizations.
 */
public class ExecutionSpecification {

    /**
     * Create a specification based on query parameters with optimized predicates.
     */
    public static Specification<Execution> withQueryParams(ExecutionQueryParams params) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Optimize by adding most selective filters first
            
            // ID filter (most selective - exact match)
            if (params.getId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), params.getId()));
                // If ID is provided, return early as it's the most selective
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }
            
            // Security ID filter (highly selective)
            if (params.getSecurityId() != null && !params.getSecurityId().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.upper(root.get("securityId")), 
                    params.getSecurityId().trim().toUpperCase()
                ));
            }
            
            // Execution status filter (moderately selective, indexed)
            if (params.getExecutionStatus() != null && !params.getExecutionStatus().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.upper(root.get("executionStatus")), 
                    "%" + params.getExecutionStatus().trim().toUpperCase() + "%"
                ));
            }
            
            // Trade type filter (moderately selective, indexed)
            if (params.getTradeType() != null && !params.getTradeType().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.upper(root.get("tradeType")), 
                    "%" + params.getTradeType().trim().toUpperCase() + "%"
                ));
            }
            
            // Destination filter (less selective, but indexed)
            if (params.getDestination() != null && !params.getDestination().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.upper(root.get("destination")), 
                    "%" + params.getDestination().trim().toUpperCase() + "%"
                ));
            }
            
            // Optimization: For count queries, avoid unnecessary joins and ordering
            if (query.getResultType() == Long.class) {
                // This is a count query - no need for complex operations
                query.distinct(true);
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Optimized specification for range-based queries (useful for time-based filtering).
     */
    public static Specification<Execution> withTimeRange(java.time.OffsetDateTime startTime, 
                                                        java.time.OffsetDateTime endTime) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (startTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("receivedTimestamp"), startTime));
            }
            
            if (endTime != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("receivedTimestamp"), endTime));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Specification for finding executions by status with optimized performance.
     */
    public static Specification<Execution> withExecutionStatus(String status) {
        return (root, query, criteriaBuilder) -> {
            // Use exact match for better index utilization
            return criteriaBuilder.equal(root.get("executionStatus"), status);
        };
    }
    
    /**
     * Specification for batch ID queries with IN clause optimization.
     */
    public static Specification<Execution> withIdIn(List<Integer> ids) {
        return (root, query, criteriaBuilder) -> {
            if (ids == null || ids.isEmpty()) {
                return criteriaBuilder.conjunction(); // Always true
            }
            
            // Optimize for large ID lists by using IN clause
            if (ids.size() <= 1000) {
                return root.get("id").in(ids);
            } else {
                // For very large lists, split into chunks to avoid database limits
                List<Predicate> chunkPredicates = new ArrayList<>();
                for (int i = 0; i < ids.size(); i += 1000) {
                    int endIndex = Math.min(i + 1000, ids.size());
                    List<Integer> chunk = ids.subList(i, endIndex);
                    chunkPredicates.add(root.get("id").in(chunk));
                }
                return criteriaBuilder.or(chunkPredicates.toArray(new Predicate[0]));
            }
        };
    }
} 