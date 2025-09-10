package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for comprehensive error handling and recovery in the complete batch processing workflow.
 * Tests integration between ExecutionServiceImpl, ErrorRecoveryService, and BulkExecutionProcessor.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BatchProcessingErrorHandlingTest {

    @Mock
    private ExecutionRepository executionRepository;
    
    @Mock
    private BulkExecutionProcessor bulkExecutionProcessor;
    
    @Mock
    private AsyncKafkaPublisher asyncKafkaPublisher;
    
    @Mock
    private BatchProcessingMetrics metrics;
    
    @Mock
    private ErrorRecoveryService errorRecoveryService;
    
    @Mock
    private TradeServiceClient tradeServiceClient;
    
    @Mock
    private SecurityServiceClient securityServiceClient;
    
    private ExecutionServiceImpl executionService;
    private BatchExecutionRequestDTO batchRequest;
    private List<ExecutionPostDTO> testRequests;

    @BeforeEach
    void setUp() {
        executionService = new ExecutionServiceImpl(
            executionRepository,
            null, // KafkaTemplate not needed for these tests
            tradeServiceClient,
            securityServiceClient,
            bulkExecutionProcessor,
            asyncKafkaPublisher,
            metrics,
            errorRecoveryService,
            "test-topic"
        );
        
        setupTestData();
    }

    private void setupTestData() {
        testRequests = new ArrayList<>();
        
        // Valid execution request
        ExecutionPostDTO validRequest = new ExecutionPostDTO();
        validRequest.setExecutionStatus("NEW");
        validRequest.setTradeType("BUY");
        validRequest.setDestination("NYSE");
        validRequest.setSecurityId("AAPL001");
        validRequest.setQuantity(BigDecimal.valueOf(100));
        validRequest.setLimitPrice(BigDecimal.valueOf(150.0));
        testRequests.add(validRequest);
        
        // Invalid execution request (missing required field)
        ExecutionPostDTO invalidRequest = new ExecutionPostDTO();
        invalidRequest.setExecutionStatus("NEW");
        invalidRequest.setTradeType("BUY");
        // Missing destination
        invalidRequest.setSecurityId("GOOGL001");
        invalidRequest.setQuantity(BigDecimal.valueOf(200));
        testRequests.add(invalidRequest);
        
        // Another valid request
        ExecutionPostDTO anotherValidRequest = new ExecutionPostDTO();
        anotherValidRequest.setExecutionStatus("NEW");
        anotherValidRequest.setTradeType("SELL");
        anotherValidRequest.setDestination("NASDAQ");
        anotherValidRequest.setSecurityId("MSFT001");
        anotherValidRequest.setQuantity(BigDecimal.valueOf(50));
        anotherValidRequest.setLimitPrice(BigDecimal.valueOf(300.0));
        testRequests.add(anotherValidRequest);
        
        batchRequest = new BatchExecutionRequestDTO();
        batchRequest.setExecutions(testRequests);
    }

    @Test
    void testBatchProcessing_ValidationErrorsWithDetailedReporting() {
        // Arrange
        BulkExecutionProcessor.BatchProcessingContext processingContext = 
            new BulkExecutionProcessor.BatchProcessingContext(testRequests);
        
        // Add validation error for the invalid request
        BulkExecutionProcessor.ValidationException validationError = 
            new BulkExecutionProcessor.ValidationException(
                "Destination is required", "MISSING_REQUIRED_FIELD", "destination"
            );
        processingContext.addValidationError(1, validationError);
        
        // Add valid executions for requests 0 and 2
        Execution execution1 = createExecution(1, testRequests.get(0));
        Execution execution2 = createExecution(2, testRequests.get(2));
        processingContext.getValidatedExecutions().add(execution1);
        processingContext.getValidatedExecutions().add(null); // Invalid request
        processingContext.getValidatedExecutions().add(execution2);
        
        when(bulkExecutionProcessor.processBatch(testRequests)).thenReturn(processingContext);
        when(metrics.startBatchProcessing(3)).thenReturn(mock(io.micrometer.core.instrument.Timer.Sample.class));
        
        // Mock successful database operations
        List<Execution> savedExecutions = List.of(execution1, execution2);
        when(errorRecoveryService.bulkInsertWithFallback(any(), any())).thenReturn(savedExecutions);
        
        // Mock successful Kafka publishing
        AsyncKafkaPublisher.PublishResult successResult = AsyncKafkaPublisher.PublishResult.success(1, 0);
        when(asyncKafkaPublisher.publishAsync(any())).thenReturn(CompletableFuture.completedFuture(successResult));
        
        // Act
        BatchExecutionResponseDTO response = executionService.createBatchExecutions(batchRequest);
        
        // Assert
        assertEquals("PARTIAL_SUCCESS", response.getStatus());
        assertEquals(3, response.getTotalRequested());
        assertEquals(2, response.getSuccessful());
        assertEquals(1, response.getFailed());
        
        // Check detailed error reporting
        List<ExecutionResultDTO> results = response.getResults();
        assertEquals(3, results.size());
        
        // First execution should be successful
        assertEquals("SUCCESS", results.get(0).getStatus());
        assertNotNull(results.get(0).getExecution());
        
        // Second execution should have detailed validation error
        assertEquals("FAILED", results.get(1).getStatus());
        assertTrue(results.get(1).getMessage().contains("Destination is required"));
        assertTrue(results.get(1).getMessage().contains("Code: MISSING_REQUIRED_FIELD"));
        assertTrue(results.get(1).getMessage().contains("Field: destination"));
        assertNull(results.get(1).getExecution());
        
        // Third execution should be successful
        assertEquals("SUCCESS", results.get(2).getStatus());
        assertNotNull(results.get(2).getExecution());
    }

    @Test
    void testBatchProcessing_DatabaseErrorsWithFallbackRecovery() {
        // Arrange
        BulkExecutionProcessor.BatchProcessingContext processingContext = 
            new BulkExecutionProcessor.BatchProcessingContext(testRequests);
        
        // All requests are valid
        Execution execution1 = createExecution(1, testRequests.get(0));
        Execution execution2 = createExecution(2, testRequests.get(1));
        Execution execution3 = createExecution(3, testRequests.get(2));
        processingContext.getValidatedExecutions().addAll(List.of(execution1, execution2, execution3));
        
        when(bulkExecutionProcessor.processBatch(testRequests)).thenReturn(processingContext);
        when(metrics.startBatchProcessing(3)).thenReturn(mock(io.micrometer.core.instrument.Timer.Sample.class));
        
        // Mock database error recovery - bulk fails, fallback partially succeeds
        List<Execution> savedExecutions = List.of(execution1, execution3); // execution2 fails
        when(errorRecoveryService.bulkInsertWithFallback(any(), any())).thenAnswer(invocation -> {
            BatchProcessingContext context = invocation.getArgument(1);
            // Simulate that execution2 failed in fallback
            ErrorRecoveryService.DatabaseError dbError = new ErrorRecoveryService.DatabaseError(
                1, execution2, new RuntimeException("Database constraint violation"), 
                new RuntimeException("Bulk insert failed"), "Individual insert failed"
            );
            context.recordDatabaseError(1, dbError);
            context.recordDatabaseSuccess(0, execution1);
            context.recordDatabaseSuccess(2, execution3);
            return savedExecutions;
        });
        
        // Mock successful Kafka publishing for successful executions
        AsyncKafkaPublisher.PublishResult successResult = AsyncKafkaPublisher.PublishResult.success(1, 0);
        when(asyncKafkaPublisher.publishAsync(any())).thenReturn(CompletableFuture.completedFuture(successResult));
        
        // Act
        BatchExecutionResponseDTO response = executionService.createBatchExecutions(batchRequest);
        
        // Assert
        assertEquals("PARTIAL_SUCCESS", response.getStatus());
        assertEquals(3, response.getTotalRequested());
        assertEquals(2, response.getSuccessful());
        assertEquals(1, response.getFailed());
        
        // Verify error recovery was called
        verify(errorRecoveryService, times(1)).bulkInsertWithFallback(any(), any());
        
        // Check that database error has detailed information
        List<ExecutionResultDTO> results = response.getResults();
        ExecutionResultDTO failedResult = results.get(1);
        assertEquals("FAILED", failedResult.getStatus());
        assertTrue(failedResult.getMessage().contains("Database error"));
        assertTrue(failedResult.getMessage().contains("Database constraint violation"));
    }

    @Test
    void testBatchProcessing_KafkaErrorsWithRecovery() {
        // Arrange
        BulkExecutionProcessor.BatchProcessingContext processingContext = 
            new BulkExecutionProcessor.BatchProcessingContext(testRequests);
        
        // All requests are valid and database operations succeed
        Execution execution1 = createExecution(1, testRequests.get(0));
        Execution execution2 = createExecution(2, testRequests.get(1));
        Execution execution3 = createExecution(3, testRequests.get(2));
        processingContext.getValidatedExecutions().addAll(List.of(execution1, execution2, execution3));
        
        when(bulkExecutionProcessor.processBatch(testRequests)).thenReturn(processingContext);
        when(metrics.startBatchProcessing(3)).thenReturn(mock(io.micrometer.core.instrument.Timer.Sample.class));
        
        // Mock successful database operations
        List<Execution> savedExecutions = List.of(execution1, execution2, execution3);
        when(errorRecoveryService.bulkInsertWithFallback(any(), any())).thenAnswer(invocation -> {
            BatchProcessingContext context = invocation.getArgument(1);
            context.recordDatabaseSuccess(0, execution1);
            context.recordDatabaseSuccess(1, execution2);
            context.recordDatabaseSuccess(2, execution3);
            return savedExecutions;
        });
        
        // Mock Kafka publishing - some succeed, some fail
        AsyncKafkaPublisher.PublishResult successResult = AsyncKafkaPublisher.PublishResult.success(1, 0);
        AsyncKafkaPublisher.PublishResult failureResult = AsyncKafkaPublisher.PublishResult.failed(2, "Kafka broker unavailable", 2);
        
        when(asyncKafkaPublisher.publishAsync(any()))
            .thenReturn(CompletableFuture.completedFuture(successResult))  // First execution succeeds
            .thenReturn(CompletableFuture.completedFuture(failureResult))  // Second execution fails
            .thenReturn(CompletableFuture.completedFuture(successResult)); // Third execution succeeds
        
        // Mock Kafka recovery
        when(errorRecoveryService.recoverKafkaFailures(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // Act
        BatchExecutionResponseDTO response = executionService.createBatchExecutions(batchRequest);
        
        // Assert
        assertEquals("SUCCESS", response.getStatus()); // Database operations succeeded
        assertEquals(3, response.getTotalRequested());
        assertEquals(3, response.getSuccessful()); // All database operations succeeded
        assertEquals(0, response.getFailed());
        
        // Verify Kafka recovery was attempted for failed publications
        verify(errorRecoveryService, times(1)).recoverKafkaFailures(any(), any());
    }

    @Test
    void testBatchProcessing_CriticalDatabaseFailure() {
        // Arrange
        BulkExecutionProcessor.BatchProcessingContext processingContext = 
            new BulkExecutionProcessor.BatchProcessingContext(testRequests);
        
        // All requests are valid
        Execution execution1 = createExecution(1, testRequests.get(0));
        Execution execution2 = createExecution(2, testRequests.get(1));
        Execution execution3 = createExecution(3, testRequests.get(2));
        processingContext.getValidatedExecutions().addAll(List.of(execution1, execution2, execution3));
        
        when(bulkExecutionProcessor.processBatch(testRequests)).thenReturn(processingContext);
        when(metrics.startBatchProcessing(3)).thenReturn(mock(io.micrometer.core.instrument.Timer.Sample.class));
        
        // Mock critical database failure - even fallback fails
        RuntimeException criticalError = new RuntimeException("Database connection pool exhausted");
        when(errorRecoveryService.bulkInsertWithFallback(any(), any())).thenThrow(criticalError);
        
        // Act
        BatchExecutionResponseDTO response = executionService.createBatchExecutions(batchRequest);
        
        // Assert
        assertEquals("FAILED", response.getStatus());
        assertEquals(3, response.getTotalRequested());
        assertEquals(0, response.getSuccessful());
        assertEquals(3, response.getFailed());
        
        // All executions should have failed with detailed error information
        List<ExecutionResultDTO> results = response.getResults();
        for (ExecutionResultDTO result : results) {
            assertEquals("FAILED", result.getStatus());
            assertTrue(result.getMessage().contains("Database error"));
            assertTrue(result.getMessage().contains("Critical failure in bulk database operations"));
        }
        
        // Verify metrics recorded the critical error
        verify(metrics, times(1)).recordDatabaseError("bulk_operation_critical", "RuntimeException");
    }

    @Test
    void testBatchProcessing_MixedErrorScenario() {
        // Arrange - Complex scenario with validation errors, database errors, and Kafka errors
        List<ExecutionPostDTO> mixedRequests = new ArrayList<>();
        
        // Valid request
        ExecutionPostDTO validRequest = new ExecutionPostDTO();
        validRequest.setExecutionStatus("NEW");
        validRequest.setTradeType("BUY");
        validRequest.setDestination("NYSE");
        validRequest.setSecurityId("AAPL001");
        validRequest.setQuantity(BigDecimal.valueOf(100));
        mixedRequests.add(validRequest);
        
        // Invalid request (validation error)
        ExecutionPostDTO invalidRequest = new ExecutionPostDTO();
        invalidRequest.setExecutionStatus("INVALID_STATUS");
        invalidRequest.setTradeType("BUY");
        invalidRequest.setDestination("NYSE");
        invalidRequest.setSecurityId("GOOGL001");
        invalidRequest.setQuantity(BigDecimal.valueOf(200));
        mixedRequests.add(invalidRequest);
        
        // Valid request that will have database error
        ExecutionPostDTO dbErrorRequest = new ExecutionPostDTO();
        dbErrorRequest.setExecutionStatus("NEW");
        dbErrorRequest.setTradeType("SELL");
        dbErrorRequest.setDestination("NASDAQ");
        dbErrorRequest.setSecurityId("MSFT001");
        dbErrorRequest.setQuantity(BigDecimal.valueOf(50));
        mixedRequests.add(dbErrorRequest);
        
        // Valid request that will succeed
        ExecutionPostDTO successRequest = new ExecutionPostDTO();
        successRequest.setExecutionStatus("NEW");
        successRequest.setTradeType("BUY");
        successRequest.setDestination("NYSE");
        successRequest.setSecurityId("TSLA001");
        successRequest.setQuantity(BigDecimal.valueOf(25));
        mixedRequests.add(successRequest);
        
        BatchExecutionRequestDTO mixedBatchRequest = new BatchExecutionRequestDTO();
        mixedBatchRequest.setExecutions(mixedRequests);
        
        // Mock processing context with validation error
        BulkExecutionProcessor.BatchProcessingContext processingContext = 
            new BulkExecutionProcessor.BatchProcessingContext(mixedRequests);
        
        BulkExecutionProcessor.ValidationException validationError = 
            new BulkExecutionProcessor.ValidationException(
                "Invalid execution status", "INVALID_ENUM_VALUE", "executionStatus"
            );
        processingContext.addValidationError(1, validationError);
        
        // Valid executions for requests 0, 2, 3
        Execution execution1 = createExecution(1, mixedRequests.get(0));
        Execution execution3 = createExecution(3, mixedRequests.get(2));
        Execution execution4 = createExecution(4, mixedRequests.get(3));
        processingContext.getValidatedExecutions().add(execution1);
        processingContext.getValidatedExecutions().add(null); // Invalid request
        processingContext.getValidatedExecutions().add(execution3);
        processingContext.getValidatedExecutions().add(execution4);
        
        when(bulkExecutionProcessor.processBatch(mixedRequests)).thenReturn(processingContext);
        when(metrics.startBatchProcessing(4)).thenReturn(mock(io.micrometer.core.instrument.Timer.Sample.class));
        
        // Mock database operations - execution3 fails, others succeed
        List<Execution> savedExecutions = List.of(execution1, execution4);
        when(errorRecoveryService.bulkInsertWithFallback(any(), any())).thenAnswer(invocation -> {
            BatchProcessingContext context = invocation.getArgument(1);
            context.recordDatabaseSuccess(0, execution1);
            ErrorRecoveryService.DatabaseError dbError = new ErrorRecoveryService.DatabaseError(
                2, execution3, new RuntimeException("Constraint violation"), null, "Database constraint error"
            );
            context.recordDatabaseError(2, dbError);
            context.recordDatabaseSuccess(3, execution4);
            return savedExecutions;
        });
        
        // Mock Kafka publishing - execution4 fails
        AsyncKafkaPublisher.PublishResult successResult = AsyncKafkaPublisher.PublishResult.success(1, 0);
        AsyncKafkaPublisher.PublishResult failureResult = AsyncKafkaPublisher.PublishResult.failed(4, "Kafka timeout", 2);
        
        when(asyncKafkaPublisher.publishAsync(any()))
            .thenReturn(CompletableFuture.completedFuture(successResult))  // execution1 succeeds
            .thenReturn(CompletableFuture.completedFuture(failureResult)); // execution4 fails
        
        when(errorRecoveryService.recoverKafkaFailures(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // Act
        BatchExecutionResponseDTO response = executionService.createBatchExecutions(mixedBatchRequest);
        
        // Assert
        assertEquals("PARTIAL_SUCCESS", response.getStatus());
        assertEquals(4, response.getTotalRequested());
        assertEquals(2, response.getSuccessful()); // Only executions 1 and 4 succeeded in database
        assertEquals(2, response.getFailed()); // Validation error + database error
        
        List<ExecutionResultDTO> results = response.getResults();
        assertEquals(4, results.size());
        
        // Check each result
        assertEquals("SUCCESS", results.get(0).getStatus()); // execution1 - success
        assertEquals("FAILED", results.get(1).getStatus());  // validation error
        assertTrue(results.get(1).getMessage().contains("Invalid execution status"));
        
        assertEquals("FAILED", results.get(2).getStatus());  // database error
        assertTrue(results.get(2).getMessage().contains("Database error"));
        
        assertEquals("SUCCESS", results.get(3).getStatus()); // execution4 - success (Kafka error doesn't affect result)
        
        // Verify recovery mechanisms were called
        verify(errorRecoveryService, times(1)).bulkInsertWithFallback(any(), any());
        verify(errorRecoveryService, times(1)).recoverKafkaFailures(any(), any());
    }

    private Execution createExecution(int id, ExecutionPostDTO request) {
        Execution execution = new Execution();
        execution.setId(id);
        execution.setExecutionStatus(request.getExecutionStatus());
        execution.setTradeType(request.getTradeType());
        execution.setDestination(request.getDestination());
        execution.setSecurityId(request.getSecurityId());
        execution.setQuantity(request.getQuantity());
        execution.setLimitPrice(request.getLimitPrice());
        execution.setReceivedTimestamp(OffsetDateTime.now());
        execution.setVersion(0);
        return execution;
    }
}