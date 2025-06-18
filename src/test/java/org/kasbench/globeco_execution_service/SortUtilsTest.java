package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class SortUtilsTest {

    @Test
    void testParseSortBy_SingleFieldAscending() {
        Sort result = SortUtils.parseSortBy("id");
        
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
    
    @Test
    void testParseSortBy_SingleFieldDescending() {
        Sort result = SortUtils.parseSortBy("-id");
        
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
    
    @Test
    void testParseSortBy_MultipleFields() {
        Sort result = SortUtils.parseSortBy("id,-executionStatus,tradeType");
        
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        
        assertThat(result.getOrderFor("executionStatus")).isNotNull();
        assertThat(result.getOrderFor("executionStatus").getDirection()).isEqualTo(Sort.Direction.DESC);
        
        assertThat(result.getOrderFor("tradeType")).isNotNull();
        assertThat(result.getOrderFor("tradeType").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
    
    @Test
    void testParseSortBy_EmptyString() {
        Sort result = SortUtils.parseSortBy("");
        
        // Should default to id ASC
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
    
    @Test
    void testParseSortBy_NullString() {
        Sort result = SortUtils.parseSortBy(null);
        
        // Should default to id ASC
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
    
    @Test
    void testParseSortBy_InvalidField() {
        Sort result = SortUtils.parseSortBy("invalidField");
        
        // Should default to id ASC when all fields are invalid
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(result.getOrderFor("invalidField")).isNull();
    }
    
    @Test
    void testParseSortBy_MixedValidInvalidFields() {
        Sort result = SortUtils.parseSortBy("invalidField,id,-executionStatus,anotherInvalidField");
        
        // Should only include valid fields
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        
        assertThat(result.getOrderFor("executionStatus")).isNotNull();
        assertThat(result.getOrderFor("executionStatus").getDirection()).isEqualTo(Sort.Direction.DESC);
        
        assertThat(result.getOrderFor("invalidField")).isNull();
        assertThat(result.getOrderFor("anotherInvalidField")).isNull();
    }
    
    @Test
    void testParseSortBy_WhitespaceHandling() {
        Sort result = SortUtils.parseSortBy(" id , -executionStatus , tradeType ");
        
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        
        assertThat(result.getOrderFor("executionStatus")).isNotNull();
        assertThat(result.getOrderFor("executionStatus").getDirection()).isEqualTo(Sort.Direction.DESC);
        
        assertThat(result.getOrderFor("tradeType")).isNotNull();
        assertThat(result.getOrderFor("tradeType").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
    
    @Test
    void testParseSortBy_AllValidFields() {
        String allFields = "id,executionStatus,tradeType,destination,securityId,quantity,receivedTimestamp,sentTimestamp";
        Sort result = SortUtils.parseSortBy(allFields);
        
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("executionStatus")).isNotNull();
        assertThat(result.getOrderFor("tradeType")).isNotNull();
        assertThat(result.getOrderFor("destination")).isNotNull();
        assertThat(result.getOrderFor("securityId")).isNotNull();
        assertThat(result.getOrderFor("quantity")).isNotNull();
        assertThat(result.getOrderFor("receivedTimestamp")).isNotNull();
        assertThat(result.getOrderFor("sentTimestamp")).isNotNull();
    }
    
    @Test
    void testParseSortBy_EmptyFieldsIgnored() {
        Sort result = SortUtils.parseSortBy("id,,executionStatus,,,tradeType,");
        
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("executionStatus")).isNotNull();
        assertThat(result.getOrderFor("tradeType")).isNotNull();
    }
    
    @Test
    void testGetValidSortFields() {
        var validFields = SortUtils.getValidSortFields();
        
        assertThat(validFields).containsExactlyInAnyOrder(
            "id", "executionStatus", "tradeType", "destination", 
            "securityId", "quantity", "receivedTimestamp", "sentTimestamp"
        );
    }
} 