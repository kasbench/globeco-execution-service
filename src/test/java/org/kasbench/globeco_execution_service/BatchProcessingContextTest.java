package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BatchProcessingContext class.
 * Tests context management, state tracking, error handling, and result aggregation.
 */
class BatchProcessingContextTest {
    
    private BatchProcessingContext context;
    private List<ExecutionPostDTO> sampleRequests;
    private List<Execution> sampleExecutions;
    
    @BeforeEach
    void setUp() {
        // Create sample execution requests
        sampleRequests = Arrays.asList(
            createExecutionPostDTO("NEW", "BUY", "NYSE", "AAPL123456789012345678", 
                new BigDecimal("100"), new BigDecimal("150.00"), 1),
            createExecutionPostDTO("NEW", "SELL", "NASDAQ", "GOOGL12345678901234567", 
                new BigDecimal("50"), new BigDecimal("2500.00"), 1),
            createExecutionPostDTO("PART", "BUY", "NYSE", "MSFT123456789012345678", 
                new BigDecimal("200"), new BigDecimal("300.00"), 1)
        );
        
        // Create sample execution entities
        sampleExecutions = Arrays.asList(
            createExecution(1, "NEW", "BUY", "NYSE", "AAPL123456789012345678", 
                new BigDecimal("100"), new BigDecimal("150.00"), 1),
            createExecution(2, "NEW", "SELL", "NASDAQ", "GOOGL12345678901234567", 
                new BigDecimal("50"), new BigDecimal("2500.00"), 1),
            createExecution(3, "PART", "BUY", "NYSE", "MSFT123456789012345678", 
                new BigDecimal("200"), new BigDecimal("300.00"), 1)
        );
        
        context = new BatchProcessingContext(sampleRequests);
    }
    
    @Test
    @DisplayName("Should initialize context with correct initial state")
    void shouldInitializeContextCorrectly() {
        assertEquals(3, context.getOriginalRequests().size());
        assertEquals(BatchProcessingContext.ProcessingPhase.VALIDATION, context.getCurrentPhase());
        assertFalse(context.isProcessingComplete());
        assertNotNull(context.getProcessingStartTime());
        assertNull(context.getProcessingEndTime());
        assertEquals(0, context.getProcessedCount());
        
        // Results should be initialized with nulls
        assertEquals(3, context.getResults().size());
        assertTrue(context.getResults().stream().allMatch(Objects::isNull));
    }
    
    @Test
    @DisplayName("Should handle validation errors correctly")
    void shouldHandleValidationErrors() {
        Exception validationError = new IllegalArgumentException("Invalid security ID");
        
        context.addValidationError(0, validationError);
        
        Set<Integer> errorIndices = context.getValidationErrorIndices();
        assertEquals(1, errorIndices.size());
        assertTrue(errorIndices.contains(0));
        
        List<ExecutionResultDTO> results = context.getResults();
        ExecutionResultDTO result = results.get(0);
        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertEquals("Invalid security ID", result.getMessage());
        assertEquals(0, result.getRequestIndex());
        assertNull(result.getExecution());
    }
    
    @Test
    @DisplayName("Should handle validated executions correctly")
    void shouldHandleValidatedExecutions() {
        context.addValidatedExecution(0, sampleExecutions.get(0));
        context.addValidatedExecution(1, sampleExecutions.get(1));
        
        List<Execution> validatedExecutions = context.getValidatedExecutions();
        assertEquals(2, validatedExecutions.size());
        assertEquals(sampleExecutions.get(0), validatedExecutions.get(0));
        assertEquals(sampleExecutions.get(1), validatedExecutions.get(1));
        
        List<Execution> validExecutionsOnly = context.getValidExecutionsOnly();
        assertEquals(2, validExecutionsOnly.size());
        assertFalse(validExecutionsOnly.contains(null));
    }
    
    @Test
    @DisplayName("Should record database success correctly")
    void shouldRecordDatabaseSuccess() {
        Execution savedExecution = sampleExecutions.get(0);
        
        context.recordDatabaseSuccess(0, savedExecution);
        
        Set<Integer> successfulIndices = context.getSuccessfulDatabaseIndices();
        assertEquals(1, successfulIndices.size());
        assertTrue(successfulIndices.contains(0));
        assertEquals(1, context.getProcessedCount());
        
        List<ExecutionResultDTO> results = context.getResults();
        ExecutionResultDTO result = results.get(0);
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertNull(result.getMessage());
        assertEquals(0, result.getRequestIndex());
        assertNotNull(result.getExecution());
        assertEquals(savedExecution.getId(), result.getExecution().getId());
    }
    
    @Test
    @DisplayName("Should record database errors correctly")
    void shouldRecordDatabaseErrors() {
        Exception dbError = new RuntimeException("Database connection failed");
        
        context.recordDatabaseError(1, dbError);
        
        Set<Integer> errorIndices = context.getDatabaseErrorIndices();
        assertEquals(1, errorIndices.size());
        assertTrue(errorIndices.contains(1));
        assertEquals(1, context.getProcessedCount());
        
        List<ExecutionResultDTO> results = context.getResults();
        ExecutionResultDTO result = results.get(1);
        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertEquals("Database error: Database connection failed", result.getMessage());
        assertEquals(1, result.getRequestIndex());
        assertNull(result.getExecution());
    }
    
    @Test
    @DisplayName("Should record Kafka success correctly")
    void shouldRecordKafkaSuccess() {
        context.recordKafkaSuccess(0);
        
        Set<Integer> successfulIndices = context.getSuccessfulKafkaIndices();
        assertEquals(1, successfulIndices.size());
        assertTrue(successfulIndices.contains(0));
    }
    
    @Test
    @DisplayName("Should record Kafka errors without affecting execution success")
    void shouldRecordKafkaErrors() {
        Exception kafkaError = new RuntimeException("Kafka broker unavailable");
        
        // First record a successful database operation
        context.recordDatabaseSuccess(0, sampleExecutions.get(0));
        
        // Then record a Kafka error
        context.recordKafkaError(0, kafkaError);
        
        Set<Integer> kafkaErrorIndices = context.getKafkaErrorIndices();
        assertEquals(1, kafkaErrorIndices.size());
        assertTrue(kafkaErrorIndices.contains(0));
        
        // The execution should still be marked as successful since database succeeded
        List<ExecutionResultDTO> results = context.getResults();
        ExecutionResultDTO result = results.get(0);
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
    }
    
    @Test
    @DisplayName("Should track processing phases correctly")
    void shouldTrackProcessingPhases() {
        assertEquals(BatchProcessingContext.ProcessingPhase.VALIDATION, context.getCurrentPhase());
        assertFalse(context.isProcessingComplete());
        assertNull(context.getProcessingEndTime());
        
        context.setProcessingPhase(BatchProcessingContext.ProcessingPhase.DATABASE_OPERATIONS);
        assertEquals(BatchProcessingContext.ProcessingPhase.DATABASE_OPERATIONS, context.getCurrentPhase());
        assertFalse(context.isProcessingComplete());
        
        context.setProcessingPhase(BatchProcessingContext.ProcessingPhase.COMPLETED);
        assertEquals(BatchProcessingContext.ProcessingPhase.COMPLETED, context.getCurrentPhase());
        assertTrue(context.isProcessingComplete());
        assertNotNull(context.getProcessingEndTime());
    }
    
    @Test
    @DisplayName("Should check database processing completion correctly")
    void shouldCheckDatabaseProcessingCompletion() {
        // Add validated executions
        context.addValidatedExecution(0, sampleExecutions.get(0));
        context.addValidatedExecution(1, sampleExecutions.get(1));
        
        assertFalse(context.isDatabaseProcessingComplete());
        
        // Record one success and one error
        context.recordDatabaseSuccess(0, sampleExecutions.get(0));
        assertFalse(context.isDatabaseProcessingComplete());
        
        context.recordDatabaseError(1, new RuntimeException("DB Error"));
        assertTrue(context.isDatabaseProcessingComplete());
    }
    
    @Test
    @DisplayName("Should check Kafka processing completion correctly")
    void shouldCheckKafkaProcessingCompletion() {
        // Record successful database operations
        context.recordDatabaseSuccess(0, sampleExecutions.get(0));
        context.recordDatabaseSuccess(1, sampleExecutions.get(1));
        
        assertFalse(context.isKafkaProcessingComplete());
        
        // Record one Kafka success and one error
        context.recordKafkaSuccess(0);
        assertFalse(context.isKafkaProcessingComplete());
        
        context.recordKafkaError(1, new RuntimeException("Kafka Error"));
        assertTrue(context.isKafkaProcessingComplete());
    }
    
    @Test
    @DisplayName("Should generate correct batch response")
    void shouldGenerateCorrectBatchResponse() {
        // Set up mixed results
        context.addValidationError(0, new IllegalArgumentException("Validation failed"));
        context.recordDatabaseSuccess(1, sampleExecutions.get(1));
        context.recordDatabaseError(2, new RuntimeException("DB Error"));
        
        BatchExecutionResponseDTO response = context.getBatchResponse();
        
        assertNotNull(response);
        assertEquals("PARTIAL_SUCCESS", response.getStatus());
        assertEquals(3, response.getTotalRequested());
        assertEquals(1, response.getSuccessful());
        assertEquals(2, response.getFailed());
        assertEquals("1 of 3 executions created successfully", response.getMessage());
        assertEquals(3, response.getResults().size());
    }
    
    @Test
    @DisplayName("Should provide comprehensive processing statistics")
    void shouldProvideProcessingStatistics() {
        // Set up some processing state
        context.addValidationError(0, new IllegalArgumentException("Validation failed"));
        context.recordDatabaseSuccess(1, sampleExecutions.get(1));
        context.recordKafkaError(1, new RuntimeException("Kafka error"));
        context.setProcessingPhase(BatchProcessingContext.ProcessingPhase.COMPLETED);
        
        Map<String, Object> stats = context.getProcessingStatistics();
        
        assertEquals(3, stats.get("totalRequests"));
        assertEquals(1, stats.get("validationErrors"));
        assertEquals(0, stats.get("databaseErrors"));
        assertEquals(1, stats.get("kafkaErrors"));
        assertEquals(1, stats.get("successfulDatabaseOperations"));
        assertEquals(0, stats.get("successfulKafkaOperations"));
        assertEquals("COMPLETED", stats.get("currentPhase"));
        assertEquals(true, stats.get("processingComplete"));
        assertNotNull(stats.get("processingStartTime"));
        assertNotNull(stats.get("processingEndTime"));
        assertNotNull(stats.get("processingDurationMs"));
    }
    
    @Test
    @DisplayName("Should provide all error details")
    void shouldProvideAllErrorDetails() {
        context.addValidationError(0, new IllegalArgumentException("Invalid data"));
        context.recordDatabaseError(1, new RuntimeException("DB connection failed"));
        context.recordKafkaError(2, new RuntimeException("Kafka timeout"));
        
        Map<String, Map<Integer, String>> allErrors = context.getAllErrors();
        
        assertEquals(3, allErrors.size());
        assertTrue(allErrors.containsKey("validation"));
        assertTrue(allErrors.containsKey("database"));
        assertTrue(allErrors.containsKey("kafka"));
        
        assertEquals("Invalid data", allErrors.get("validation").get(0));
        assertEquals("DB connection failed", allErrors.get("database").get(1));
        assertEquals("Kafka timeout", allErrors.get("kafka").get(2));
    }
    
    @Test
    @DisplayName("Should handle concurrent operations safely")
    void shouldHandleConcurrentOperationsSafely() throws InterruptedException {
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        
        // Create threads that will concurrently update the context
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                if (threadIndex % 2 == 0) {
                    context.recordDatabaseSuccess(threadIndex % 3, sampleExecutions.get(threadIndex % 3));
                } else {
                    context.recordKafkaSuccess(threadIndex % 3);
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify that the context handled concurrent updates correctly
        assertTrue(context.getSuccessfulDatabaseIndices().size() > 0);
        assertTrue(context.getSuccessfulKafkaIndices().size() > 0);
        assertTrue(context.getProcessedCount() > 0);
    }
    
    @Test
    @DisplayName("Should handle edge case with empty request list")
    void shouldHandleEmptyRequestList() {
        BatchProcessingContext emptyContext = new BatchProcessingContext(Collections.emptyList());
        
        assertEquals(0, emptyContext.getOriginalRequests().size());
        assertEquals(0, emptyContext.getResults().size());
        assertTrue(emptyContext.isDatabaseProcessingComplete());
        assertTrue(emptyContext.isKafkaProcessingComplete());
        
        BatchExecutionResponseDTO response = emptyContext.getBatchResponse();
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(0, response.getTotalRequested());
        assertEquals(0, response.getSuccessful());
        assertEquals(0, response.getFailed());
    }
    
    @Test
    @DisplayName("Should handle large batch sizes efficiently")
    void shouldHandleLargeBatchSizes() {
        List<ExecutionPostDTO> largeRequestList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeRequestList.add(createExecutionPostDTO("NEW", "BUY", "NYSE", 
                "STOCK" + String.format("%019d", i), new BigDecimal("100"), 
                new BigDecimal("50.00"), 1));
        }
        
        BatchProcessingContext largeContext = new BatchProcessingContext(largeRequestList);
        
        // Simulate processing all requests successfully
        for (int i = 0; i < 1000; i++) {
            Execution execution = createExecution(i + 1, "NEW", "BUY", "NYSE", 
                "STOCK" + String.format("%019d", i), new BigDecimal("100"), 
                new BigDecimal("50.00"), 1);
            largeContext.recordDatabaseSuccess(i, execution);
            largeContext.recordKafkaSuccess(i);
        }
        
        assertEquals(1000, largeContext.getSuccessfulDatabaseIndices().size());
        assertEquals(1000, largeContext.getSuccessfulKafkaIndices().size());
        assertEquals(1000, largeContext.getProcessedCount());
        
        BatchExecutionResponseDTO response = largeContext.getBatchResponse();
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1000, response.getTotalRequested());
        assertEquals(1000, response.getSuccessful());
        assertEquals(0, response.getFailed());
    }
    
    // Helper methods
    
    private ExecutionPostDTO createExecutionPostDTO(String status, String tradeType, String destination,
                                                   String securityId, BigDecimal quantity, BigDecimal limitPrice, 
                                                   Integer version) {
        return new ExecutionPostDTO(status, tradeType, destination, securityId, quantity, limitPrice, null, version);
    }
    
    private Execution createExecution(Integer id, String status, String tradeType, String destination,
                                    String securityId, BigDecimal quantity, BigDecimal limitPrice, Integer version) {
        return new Execution(id, status, tradeType, destination, securityId, quantity, limitPrice,
            OffsetDateTime.now(), null, null, null, null, version);
    }
}