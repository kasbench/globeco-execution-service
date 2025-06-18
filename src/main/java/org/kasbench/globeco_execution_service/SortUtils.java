package org.kasbench.globeco_execution_service;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility class for handling sorting parameters.
 */
public class SortUtils {
    
    // Valid sortable fields as per requirements
    private static final Set<String> VALID_SORT_FIELDS = Set.of(
        "id", "executionStatus", "tradeType", "destination", 
        "ticker", "quantity", "receivedTimestamp", "sentTimestamp"
    );
    
    /**
     * Parse comma-separated sortBy parameter into Spring Data Sort object.
     * Supports minus (-) prefix for descending order.
     * Maps ticker sorting to securityId since that's the actual entity field.
     * 
     * @param sortBy Comma-separated list of fields with optional minus prefix
     * @return Sort object for Spring Data queries
     */
    public static Sort parseSortBy(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return Sort.by(Sort.Direction.ASC, "id"); // Default sort
        }
        
        List<Sort.Order> orders = new ArrayList<>();
        String[] fields = sortBy.split(",");
        
        for (String field : fields) {
            field = field.trim();
            if (field.isEmpty()) {
                continue;
            }
            
            Sort.Direction direction = Sort.Direction.ASC;
            String fieldName = field;
            
            // Check for descending order (minus prefix)
            if (field.startsWith("-")) {
                direction = Sort.Direction.DESC;
                fieldName = field.substring(1);
            }
            
            // Validate field name
            if (VALID_SORT_FIELDS.contains(fieldName)) {
                // Map ticker to securityId for entity-level sorting
                String entityFieldName = "ticker".equals(fieldName) ? "securityId" : fieldName;
                orders.add(new Sort.Order(direction, entityFieldName));
            } else {
                // Invalid field names are ignored as per requirements
                continue;
            }
        }
        
        // If no valid fields were found, default to id ASC
        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.ASC, "id"));
        }
        
        return Sort.by(orders);
    }
    
    /**
     * Get the set of valid sortable fields.
     * 
     * @return Set of valid field names
     */
    public static Set<String> getValidSortFields() {
        return VALID_SORT_FIELDS;
    }
} 