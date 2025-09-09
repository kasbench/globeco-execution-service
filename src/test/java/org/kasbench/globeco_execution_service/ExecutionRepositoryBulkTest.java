package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for bulk operations in ExecutionRepository.
 * Tests the performance-optimized bulk insert and update operations.
 */
@DataJpaTest
@ActiveProfiles("test")
class ExecutionRepositoryBulkTest {

    @Autowired
    private ExecutionRepository executionRepository;

    private List<Execution> testExecutions;

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        executionRepository.deleteAll();
        
        // Create test executions
        testExecutions = createTestExecutions(5);
    }

    @Test
    void bulkInsert_WithValidExecutions_ShouldInsertAllAndReturnWithIds() {
        // When
        List<Execution> insertedExecutions = executionRepository.bulkInsert(testExecutions);

        // Then
        assertThat(insertedExecutions).hasSize(5);
        assertThat(insertedExecutions).allMatch(execution -> execution.getId() != null);
        assertThat(insertedExecutions).allMatch(execution -> execution.getId() > 0);
        
        // Verify all executions were actually inserted
        long count = executionRepository.count();
        assertThat(count).isEqualTo(5);
    }

    @Test
    void bulkInsert_WithEmptyList_ShouldReturnEmptyList() {
        // When
        List<Execution> result = executionRepository.bulkInsert(new ArrayList<>());

        // Then
        assertThat(result).isEmpty();
        assertThat(executionRepository.count()).isZero();
    }

    @Test
    void bulkInsert_WithNullList_ShouldReturnEmptyList() {
        // When
        List<Execution> result = executionRepository.bulkInsert(null);

        // Then
        assertThat(result).isEmpty();
        assertThat(executionRepository.count()).isZero();
    }

    @Test
    void bulkInsert_WithLargeBatch_ShouldHandleEfficiently() {
        // Given - Create a larger batch to test performance
        List<Execution> largeBatch = createTestExecutions(100);

        // When
        List<Execution> insertedExecutions = executionRepository.bulkInsert(largeBatch);

        // Then
        assertThat(insertedExecutions).hasSize(100);
        assertThat(insertedExecutions).allMatch(execution -> execution.getId() != null);
        assertThat(executionRepository.count()).isEqualTo(100);
    }

    @Test
    void bulkInsert_WithMixedData_ShouldHandleNullableFields() {
        // Given - Create executions with some nullable fields set to null
        List<Execution> executionsWithNulls = new ArrayList<>();
        
        Execution execution1 = createTestExecution(1);
        execution1.setLimitPrice(null);
        execution1.setSentTimestamp(null);
        execution1.setTradeServiceExecutionId(null);
        execution1.setQuantityFilled(null);
        execution1.setAveragePrice(null);
        executionsWithNulls.add(execution1);
        
        Execution execution2 = createTestExecution(2);
        executionsWithNulls.add(execution2);

        // When
        List<Execution> insertedExecutions = executionRepository.bulkInsert(executionsWithNulls);

        // Then
        assertThat(insertedExecutions).hasSize(2);
        assertThat(insertedExecutions.get(0).getLimitPrice()).isNull();
        assertThat(insertedExecutions.get(0).getSentTimestamp()).isNull();
        assertThat(insertedExecutions.get(1).getLimitPrice()).isNotNull();
    }

    @Test
    void bulkUpdateSentTimestamp_WithValidIds_ShouldUpdateAllRecords() {
        // Given - Insert some executions first
        List<Execution> insertedExecutions = executionRepository.bulkInsert(testExecutions);
        List<Integer> executionIds = insertedExecutions.stream()
                .map(Execution::getId)
                .toList();
        OffsetDateTime newTimestamp = OffsetDateTime.now();

        // When
        int updatedCount = executionRepository.bulkUpdateSentTimestamp(executionIds, newTimestamp);

        // Then
        assertThat(updatedCount).isEqualTo(5);
        
        // Verify the timestamps were actually updated
        List<Execution> updatedExecutions = executionRepository.findAllById(executionIds);
        assertThat(updatedExecutions).allMatch(execution -> 
            execution.getSentTimestamp() != null && 
            execution.getSentTimestamp().isEqual(newTimestamp));
    }

    @Test
    void bulkUpdateSentTimestamp_WithEmptyIdList_ShouldUpdateZeroRecords() {
        // Given
        executionRepository.bulkInsert(testExecutions);
        OffsetDateTime newTimestamp = OffsetDateTime.now();

        // When
        int updatedCount = executionRepository.bulkUpdateSentTimestamp(new ArrayList<>(), newTimestamp);

        // Then
        assertThat(updatedCount).isZero();
    }

    @Test
    void bulkUpdateSentTimestamp_WithNonExistentIds_ShouldUpdateZeroRecords() {
        // Given
        List<Integer> nonExistentIds = List.of(999, 1000, 1001);
        OffsetDateTime newTimestamp = OffsetDateTime.now();

        // When
        int updatedCount = executionRepository.bulkUpdateSentTimestamp(nonExistentIds, newTimestamp);

        // Then
        assertThat(updatedCount).isZero();
    }

    @Test
    void bulkUpdateSentTimestamp_WithMixedValidAndInvalidIds_ShouldUpdateOnlyValidOnes() {
        // Given - Insert some executions
        List<Execution> insertedExecutions = executionRepository.bulkInsert(testExecutions);
        List<Integer> mixedIds = new ArrayList<>();
        mixedIds.add(insertedExecutions.get(0).getId()); // Valid ID
        mixedIds.add(insertedExecutions.get(1).getId()); // Valid ID
        mixedIds.add(999); // Invalid ID
        mixedIds.add(1000); // Invalid ID
        
        OffsetDateTime newTimestamp = OffsetDateTime.now();

        // When
        int updatedCount = executionRepository.bulkUpdateSentTimestamp(mixedIds, newTimestamp);

        // Then
        assertThat(updatedCount).isEqualTo(2); // Only the valid IDs should be updated
    }

    @Test
    void bulkOperations_IntegrationTest_ShouldWorkTogether() {
        // Given - Create and insert executions
        List<Execution> insertedExecutions = executionRepository.bulkInsert(testExecutions);
        assertThat(insertedExecutions).hasSize(5);

        // When - Update their sent timestamps
        List<Integer> executionIds = insertedExecutions.stream()
                .map(Execution::getId)
                .toList();
        OffsetDateTime sentTimestamp = OffsetDateTime.now();
        int updatedCount = executionRepository.bulkUpdateSentTimestamp(executionIds, sentTimestamp);

        // Then - Verify both operations worked correctly
        assertThat(updatedCount).isEqualTo(5);
        
        List<Execution> finalExecutions = executionRepository.findAllById(executionIds);
        assertThat(finalExecutions).hasSize(5);
        assertThat(finalExecutions).allMatch(execution -> 
            execution.getSentTimestamp() != null && 
            execution.getSentTimestamp().isEqual(sentTimestamp));
    }

    private List<Execution> createTestExecutions(int count) {
        return IntStream.range(1, count + 1)
                .mapToObj(this::createTestExecution)
                .toList();
    }

    private Execution createTestExecution(int index) {
        Execution execution = new Execution();
        execution.setExecutionStatus("PENDING");
        execution.setTradeType("BUY");
        execution.setDestination("NYSE");
        execution.setSecurityId("AAPL" + index);
        execution.setQuantity(new BigDecimal("100.00"));
        execution.setLimitPrice(new BigDecimal("150.00"));
        execution.setReceivedTimestamp(OffsetDateTime.now());
        execution.setQuantityFilled(new BigDecimal("0.00"));
        execution.setAveragePrice(new BigDecimal("0.00"));
        execution.setVersion(0);
        return execution;
    }
}