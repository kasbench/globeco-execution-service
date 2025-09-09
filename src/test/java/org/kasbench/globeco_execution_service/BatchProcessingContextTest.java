package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BatchProcessingContext.
 */
class BatchProcessingContextTest {

    @Test
    void batchProcessingContext_ShouldTrackStateCorrectly() {
        // Given
        List<ExecutionPostDTO> requests = createValidExecutionRequests(3);
        BulkExecutionProcessor.BatchProcessingContext context = 
            new BulkExecutionProcessor.BatchProcessingContext(requests);

        // When
        context.addValidationError(1, new BulkExecutionProcessor.ValidationException("Test error"));
        context.addResult(ExecutionResultDTO.success(0, null));
        context.addResult(ExecutionResultDTO.failure(1, "Test error"));
        context.addResult(ExecutionResultDTO.success(2, null));

        // Then
        assertThat(context.getTotalRequested()).isEqualTo(3);
        assertThat(context.hasValidationError(1)).isTrue();
        assertThat(context.hasValidationError(0)).isFalse();
        assertThat(context.getResults()).hasSize(3);
        assertThat(context.getResults().get(1).getStatus()).isEqualTo("FAILED");
    }

    private List<ExecutionPostDTO> createValidExecutionRequests(int count) {
        return List.of(
            createValidExecutionRequest(),
            createValidExecutionRequest(),
            createValidExecutionRequest()
        );
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
}