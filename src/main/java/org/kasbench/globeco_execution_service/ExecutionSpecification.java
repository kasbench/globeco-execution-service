package org.kasbench.globeco_execution_service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA Specifications for dynamic Execution queries with performance optimizations.
 */
public class ExecutionSpecification {

    /**
     * Create a specification based on query parameters with optimized predicates.
     * This method requires a SecurityServiceClient to resolve ticker to securityId.
     */
    @SuppressWarnings("null")
    public static Specification<Execution> withQueryParams(ExecutionQueryParams params, SecurityServiceClient securityServiceClient) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Optimize by adding most selective filters first
            
            // ID filter (most selective - exact match)
            if (params.getId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), params.getId()));
                // If ID is provided, return early as it's the most selective
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }
            
            // Ticker filter (highly selective) - resolve to security ID
            if (params.getTicker() != null && !params.getTicker().trim().isEmpty()) {
                try {
                    Optional<String> securityIdOpt = securityServiceClient.getSecurityIdByTicker(params.getTicker());
                    if (securityIdOpt.isPresent()) {
                        predicates.add(criteriaBuilder.equal(
                            root.get("securityId"), 
                            securityIdOpt.get()
                        ));
                    } else {
                        // If ticker not found, add a predicate that will return no results
                        predicates.add(criteriaBuilder.equal(root.get("id"), -1));
                    }
                } catch (Exception e) {
                    // If there's an error resolving the ticker, add a predicate that will return no results
                    predicates.add(criteriaBuilder.equal(root.get("id"), -1));
                }
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