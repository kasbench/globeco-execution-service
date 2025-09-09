package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BulkExecutionProcessor.
 * Tests validation, batch splitting, and error handling functionality.
 */
@ExtendWith(MockitoExtension.class)
class BulkExecutionProcessorTest {

    @Mock
    private BatchExecutionProperties batchProperties;

    private BulkExecutionProcessor processor;

    @BeforeEach
    void setUp() {
        // Set up default mock behavior - only mock what we actually use
        when(batchProperties.getBulkInsertBatchSize()).thenReturn(500);

        processor = new BulkExecutionProcessor(batchProperties);
    }

    @Test
    void processBatch_WithValidExecutions_ShouldValidateAndPrepareAll() {
        // Given
        List<ExecutionPostDTO> requests = createValidExecutionRequests(3);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getTotalRequested()).isEqualTo(3);
        assertThat(context.getValidCount()).isEqualTo(3);
        assertThat(context.getErrorCount()).isZero();
        assertThat(context.getValidatedExecutions()).hasSize(3);
        assertThat(context.getExecutionBatches()).hasSize(1);
        assertThat(context.getExecutionBatches().get(0)).hasSize(3);
    }

    @Test
    void processBatch_WithEmptyList_ShouldHandleGracefully() {
        // Given
        List<ExecutionPostDTO> requests = new ArrayList<>();

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getTotalRequested()).isZero();
        assertThat(context.getValidCount()).isZero();
        assertThat(context.getErrorCount()).isZero();
        assertThat(context.getValidatedExecutions()).isEmpty();
        assertThat(context.getExecutionBatches()).hasSize(1);
        assertThat(context.getExecutionBatches().get(0)).isEmpty();
    }

    @Test
    void processBatch_WithMixedValidAndInvalidExecutions_ShouldSeparateCorrectly() {
        // Given
        List<ExecutionPostDTO> requests = new ArrayList<>();
        requests.add(createValidExecutionRequest()); // Valid
        requests.add(createInvalidExecutionRequest()); // Invalid - missing required field
        requests.add(createValidExecutionRequest()); // Valid

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getTotalRequested()).isEqualTo(3);
        assertThat(context.getValidCount()).isEqualTo(2);
        assertThat(context.getErrorCount()).isEqualTo(1);
        assertThat(context.getValidationErrors()).hasSize(1);
        assertThat(context.getValidationErrors()).containsKey(1); // Second request (index 1) should be invalid
    }

    @Test
    void processBatch_WithLargeBatch_ShouldSplitIntoBatches() {
        // Given
        when(batchProperties.getBulkInsertBatchSize()).thenReturn(100); // Smaller batch size for testing
        List<ExecutionPostDTO> requests = createValidExecutionRequests(250);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getTotalRequested()).isEqualTo(250);
        assertThat(context.getValidCount()).isEqualTo(250);
        assertThat(context.getErrorCount()).isZero();
        assertThat(context.getExecutionBatches()).hasSize(3); // 100, 100, 50
        assertThat(context.getExecutionBatches().get(0)).hasSize(100);
        assertThat(context.getExecutionBatches().get(1)).hasSize(100);
        assertThat(context.getExecutionBatches().get(2)).hasSize(50);
    }

    @Test
    void processBatch_WithNullExecutionRequest_ShouldHandleValidationError() {
        // Given
        List<ExecutionPostDTO> requests = new ArrayList<>();
        requests.add(createValidExecutionRequest());
        requests.add(null); // Null request
        requests.add(createValidExecutionRequest());

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getTotalRequested()).isEqualTo(3);
        assertThat(context.getValidCount()).isEqualTo(2);
        assertThat(context.getErrorCount()).isEqualTo(1);
        assertThat(context.getValidationErrors().get(1).getMessage()).contains("cannot be null");
    }

    @Test
    void processBatch_WithInvalidTradeType_ShouldFailValidation() {
        // Given
        ExecutionPostDTO invalidRequest = createValidExecutionRequest();
        invalidRequest.setTradeType("INVALID");
        List<ExecutionPostDTO> requests = List.of(invalidRequest);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getValidCount()).isZero();
        assertThat(context.getErrorCount()).isEqualTo(1);
        assertThat(context.getValidationErrors().get(0).getMessage()).contains("Invalid trade type");
    }

    @Test
    void processBatch_WithInvalidExecutionStatus_ShouldFailValidation() {
        // Given
        ExecutionPostDTO invalidRequest = createValidExecutionRequest();
        invalidRequest.setExecutionStatus("INVALID_STATUS");
        List<ExecutionPostDTO> requests = List.of(invalidRequest);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getValidCount()).isZero();
        assertThat(context.getErrorCount()).isEqualTo(1);
        assertThat(context.getValidationErrors().get(0).getMessage()).contains("Invalid execution status");
    }

    @Test
    void processBatch_WithNegativeQuantity_ShouldFailValidation() {
        // Given
        ExecutionPostDTO invalidRequest = createValidExecutionRequest();
        invalidRequest.setQuantity(new BigDecimal("-100"));
        List<ExecutionPostDTO> requests = List.of(invalidRequest);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getValidCount()).isZero();
        assertThat(context.getErrorCount()).isEqualTo(1);
        assertThat(context.getValidationErrors().get(0).getMessage()).contains("Quantity must be greater than zero");
    }

    @Test
    void processBatch_WithNegativeLimitPrice_ShouldFailValidation() {
        // Given
        ExecutionPostDTO invalidRequest = createValidExecutionRequest();
        invalidRequest.setLimitPrice(new BigDecimal("-50"));
        List<ExecutionPostDTO> requests = List.of(invalidRequest);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getValidCount()).isZero();
        assertThat(context.getErrorCount()).isEqualTo(1);
        assertThat(context.getValidationErrors().get(0).getMessage()).contains("Limit price must be greater than zero");
    }

    @Test
    void processBatch_WithTooLongFields_ShouldFailValidation() {
        // Given
        ExecutionPostDTO invalidRequest = createValidExecutionRequest();
        invalidRequest.setSecurityId("A".repeat(25)); // Exceeds 24 character limit
        List<ExecutionPostDTO> requests = List.of(invalidRequest);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getValidCount()).isZero();
        assertThat(context.getErrorCount()).isEqualTo(1);
        assertThat(context.getValidationErrors().get(0).getMessage()).contains("Security ID cannot exceed 24 characters");
    }

    @Test
    void processBatch_WithNullLimitPrice_ShouldPassValidation() {
        // Given
        ExecutionPostDTO validRequest = createValidExecutionRequest();
        validRequest.setLimitPrice(null); // Null limit price should be allowed
        List<ExecutionPostDTO> requests = List.of(validRequest);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        assertThat(context.getValidCount()).isEqualTo(1);
        assertThat(context.getErrorCount()).isZero();
        assertThat(context.getValidatedExecutions().get(0).getLimitPrice()).isNull();
    }

    @Test
    void processBatch_WithValidExecutions_ShouldSetDefaultValues() {
        // Given
        List<ExecutionPostDTO> requests = createValidExecutionRequests(1);

        // When
        BulkExecutionProcessor.BatchProcessingContext context = processor.processBatch(requests);

        // Then
        Execution execution = context.getValidatedExecutions().get(0);
        assertThat(execution.getQuantityFilled()).isEqualTo(BigDecimal.ZERO);
        assertThat(execution.getAveragePrice()).isEqualTo(BigDecimal.ZERO);
        assertThat(execution.getReceivedTimestamp()).isNotNull();
        assertThat(execution.getVersion()).isNotNull();
    }



    private List<ExecutionPostDTO> createValidExecutionRequests(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> createValidExecutionRequest())
                .toList();
    }

    private ExecutionPostDTO createValidExecutionRequest() {
        ExecutionPostDTO request = new ExecutionPostDTO();
        request.setExecutionStatus("NEW");
        request.setTradeType("BUY");
        request.setDestination("NYSE");
        request.setSecurityId("AAPL");
        request.setQuantity(new BigDecimal("100"));
        request.setLimitPrice(new BigDecimal("150.00"));
        request.setVersion(0);
        return request;
    }

    private ExecutionPostDTO createInvalidExecutionRequest() {
        ExecutionPostDTO request = new ExecutionPostDTO();
        // Missing required fields to make it invalid
        request.setTradeType("BUY");
        request.setDestination("NYSE");
        request.setQuantity(new BigDecimal("100"));
        return request;
    }
}