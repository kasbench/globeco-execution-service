package org.kasbench.globeco_execution_service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification class for dynamic filtering of executions.
 */
public class ExecutionSpecification {
    
    /**
     * Create a specification based on the provided query parameters.
     * 
     * @param queryParams The query parameters for filtering
     * @return Specification for filtering executions
     */
    public static Specification<Execution> withQueryParams(ExecutionQueryParams queryParams) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Filter by execution status
            if (queryParams.getExecutionStatus() != null && !queryParams.getExecutionStatus().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("executionStatus")), 
                    queryParams.getExecutionStatus().toLowerCase()
                ));
            }
            
            // Filter by trade type
            if (queryParams.getTradeType() != null && !queryParams.getTradeType().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("tradeType")), 
                    queryParams.getTradeType().toLowerCase()
                ));
            }
            
            // Filter by destination
            if (queryParams.getDestination() != null && !queryParams.getDestination().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("destination")), 
                    queryParams.getDestination().toLowerCase()
                ));
            }
            
            // Filter by security ID
            if (queryParams.getSecurityId() != null && !queryParams.getSecurityId().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    root.get("securityId"), 
                    queryParams.getSecurityId()
                ));
            }
            
            // Filter by execution ID
            if (queryParams.getId() != null) {
                predicates.add(criteriaBuilder.equal(
                    root.get("id"), 
                    queryParams.getId()
                ));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
} 