package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for error handling and recovery mechanisms in batch processing.
 * Tests fallback mechanisms, transient failure recovery, and detailed error reporting.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ErrorRecoveryIntegrationTest {

    @Mock
    private ExecutionRepository executionRepository;
    
    @Mock
    private BatchExecutionProperties batchProperties;
    
    @Mock
    private BatchProcessingMetrics metrics;
    
    private ErrorRecoveryService errorRecoveryService;
    private BatchProcessingContext context;
    private List<ExecutionPostDTO> testRequests;
    private List<Execution> testExecutions;

    @BeforeEach
    void setUp() {
        // Setup batch properties with test values
        BatchExecutionProperties.DatabaseProperties dbProps = new BatchExecutionProperties.DatabaseProperties();
        dbProps.setMaxRetries(3);
        dbProps.setRetryDelayMs(100);
        dbProps.setMaxRetryDelayMs(1000);
        
        when(batchProperties.getDatabase()).thenReturn(dbProps);
        
        errorRecoveryService = new ErrorRecoveryService(executionRepository, batchProperties, metrics);
        
        // Setup test data
        setupTestData();
    }

    private void setupTestData() {
        testRequests = new ArrayList<>();
        testExecutions = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            ExecutionPostDTO request = new ExecutionPostDTO();
            request.setExecutionStatus("NEW");
            request.setTradeType("BUY");
            request.setDestination("NYSE");
            request.setSecurityId("AAPL" + i);
            request.setQuantity(BigDecimal.valueOf(100 + i));
            request.setLimitPrice(BigDecimal.valueOf(150.0 + i));
            testRequests.add(request);
            
            Execution execution = new Execution();
            execution.setId(i + 1);
            execution.setExecutionStatus(request.getExecutionStatus());
            execution.setTradeType(request.getTradeType());
            execution.setDestination(request.getDestination());
            execution.setSecurityId(request.getSecurityId());
            execution.setQuantity(request.getQuantity());
            execution.setLimitPrice(request.getLimitPrice());
            execution.setReceivedTimestamp(OffsetDateTime.now());
            execution.setVersion(0);
            testExecutions.add(execution);
        }
        
        context = new BatchProcessingContext(testRequests);
    }

    @Test
    void testBulkInsertWithFallback_SuccessfulBulkInsert() {
        // Arrange
        when(executionRepository.bulkInsert(testExecutions)).thenReturn(testExecutions);
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(testExecutions, context);
        
        // Assert
        assertEquals(5, result.size());
        verify(executionRepository, times(1)).bulkInsert(testExecutions);
        verify(executionRepository, never()).saveAndFlush(any(Execution.class));
        verify(metrics, times(1)).recordBulkInsertSuccess(5);
        verify(metrics, never()).recordBulkInsertFailure(anyInt(), anyString());
    }

    @Test
    void testBulkInsertWithFallback_BulkFailsFallbackToIndividual() {
        // Arrange
        RuntimeException bulkError = new RuntimeException("Bulk insert failed");
        when(executionRepository.bulkInsert(testExecutions)).thenThrow(bulkError);
        
        // Mock individual saves to succeed
        for (Execution execution : testExecutions) {
            when(executionRepository.saveAndFlush(execution)).thenReturn(execution);
        }
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(testExecutions, context);
        
        // Assert
        assertEquals(5, result.size());
        verify(executionRepository, times(1)).bulkInsert(testExecutions);
        verify(executionRepository, times(5)).saveAndFlush(any(Execution.class));
        verify(metrics, times(1)).recordBulkInsertFailure(5, "RuntimeException");
        verify(metrics, never()).recordIndividualInsertFailure(anyString());
    }

    @Test
    void testBulkInsertWithFallback_PartialIndividualFailures() {
        // Arrange
        RuntimeException bulkError = new RuntimeException("Bulk insert failed");
        when(executionRepository.bulkInsert(testExecutions)).thenThrow(bulkError);
        
        // Mock some individual saves to fail
        when(executionRepository.saveAndFlush(testExecutions.get(0))).thenReturn(testExecutions.get(0));
        when(executionRepository.saveAndFlush(testExecutions.get(1))).thenThrow(new RuntimeException("Individual insert failed"));
        when(executionRepository.saveAndFlush(testExecutions.get(2))).thenReturn(testExecutions.get(2));
        when(executionRepository.saveAndFlush(testExecutions.get(3))).thenThrow(new RuntimeException("Individual insert failed"));
        when(executionRepository.saveAndFlush(testExecutions.get(4))).thenReturn(testExecutions.get(4));
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(testExecutions, context);
        
        // Assert
        assertEquals(3, result.size()); // Only 3 successful
        verify(executionRepository, times(1)).bulkInsert(testExecutions);
        verify(executionRepository, times(5)).saveAndFlush(any(Execution.class));
        verify(metrics, times(1)).recordBulkInsertFailure(5, "RuntimeException");
        verify(metrics, times(2)).recordIndividualInsertFailure("RuntimeException");
        
        // Verify context has correct error tracking
        assertEquals(2, context.getDatabaseErrorIndices().size());
        assertEquals(3, context.getSuccessfulDatabaseIndices().size());
    }

    @Test
    void testTransientErrorRetry_DeadlockRecovery() {
        // Arrange
        Execution execution = testExecutions.get(0);
        DeadlockLoserDataAccessException transientError = new DeadlockLoserDataAccessException("Deadlock detected", null);
        
        when(executionRepository.saveAndFlush(execution))
            .thenThrow(transientError)  // First attempt fails
            .thenThrow(transientError)  // Second attempt fails
            .thenReturn(execution);     // Third attempt succeeds
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(List.of(execution), context);
        
        // Assert
        assertEquals(1, result.size());
        verify(executionRepository, times(3)).saveAndFlush(execution); // 3 attempts total
        verify(metrics, times(1)).recordBulkInsertFailure(1, "RuntimeException"); // Bulk insert not attempted in this test
    }

    @Test
    void testTransientErrorRetry_QueryTimeoutRecovery() {
        // Arrange
        Execution execution = testExecutions.get(0);
        QueryTimeoutException transientError = new QueryTimeoutException("Query timeout");
        
        when(executionRepository.bulkInsert(List.of(execution))).thenThrow(new RuntimeException("Bulk failed"));
        when(executionRepository.saveAndFlush(execution))
            .thenThrow(transientError)  // First attempt fails
            .thenReturn(execution);     // Second attempt succeeds
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(List.of(execution), context);
        
        // Assert
        assertEquals(1, result.size());
        verify(executionRepository, times(2)).saveAndFlush(execution); // 2 attempts total
    }

    @Test
    void testTransientErrorRetry_NonTransientErrorNoRetry() {
        // Arrange
        Execution execution = testExecutions.get(0);
        IllegalArgumentException nonTransientError = new IllegalArgumentException("Invalid data");
        
        when(executionRepository.bulkInsert(List.of(execution))).thenThrow(new RuntimeException("Bulk failed"));
        when(executionRepository.saveAndFlush(execution)).thenThrow(nonTransientError);
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(List.of(execution), context);
        
        // Assert
        assertEquals(0, result.size());
        verify(executionRepository, times(1)).saveAndFlush(execution); // Only 1 attempt, no retry
        verify(metrics, times(1)).recordIndividualInsertFailure("IllegalArgumentException");
    }

    @Test
    void testTransientErrorRetry_MaxRetriesExceeded() {
        // Arrange
        Execution execution = testExecutions.get(0);
        TransientDataAccessException transientError = new TransientDataAccessException("Connection failed") {};
        
        when(executionRepository.bulkInsert(List.of(execution))).thenThrow(new RuntimeException("Bulk failed"));
        when(executionRepository.saveAndFlush(execution)).thenThrow(transientError);
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(List.of(execution), context);
        
        // Assert
        assertEquals(0, result.size());
        verify(executionRepository, times(3)).saveAndFlush(execution); // Max retries = 3
        verify(metrics, times(1)).recordIndividualInsertFailure("RuntimeException");
    }

    @Test
    void testKafkaRecovery_SuccessfulRecovery() {
        // Arrange
        List<ExecutionDTO> failedExecutions = new ArrayList<>();
        ExecutionDTO execution1 = createExecutionDTO(1);
        ExecutionDTO execution2 = createExecutionDTO(2);
        failedExecutions.add(execution1);
        failedExecutions.add(execution2);
        
        // Act
        CompletableFuture<Void> recoveryFuture = errorRecoveryService.recoverKafkaFailures(failedExecutions, context);
        
        // Assert
        assertDoesNotThrow(() -> recoveryFuture.get());
        verify(metrics, times(2)).recordKafkaRecoveryAttempt();
        verify(metrics, never()).recordKafkaRecoveryFailure();
    }

    @Test
    void testDetailedErrorReporting_DatabaseError() {
        // Arrange
        Execution execution = testExecutions.get(0);
        RuntimeException originalError = new RuntimeException("Connection timeout");
        RuntimeException bulkError = new RuntimeException("Bulk operation failed");
        
        // Act
        ErrorRecoveryService.DatabaseError dbError = new ErrorRecoveryService.DatabaseError(
            0, execution, originalError, bulkError, "Individual insert failed after bulk failure"
        );
        
        // Assert
        assertEquals(0, dbError.getRequestIndex());
        assertEquals(execution, dbError.getExecution());
        assertEquals(originalError, dbError.getOriginalError());
        assertEquals(bulkError, dbError.getBulkOperationError());
        assertEquals("Individual insert failed after bulk failure", dbError.getContext());
        assertNotNull(dbError.getErrorTimestamp());
        
        String detailedInfo = dbError.getDetailedErrorInfo();
        assertTrue(detailedInfo.contains("Request Index: 0"));
        assertTrue(detailedInfo.contains("Context: Individual insert failed after bulk failure"));
        assertTrue(detailedInfo.contains("Original Error: RuntimeException - Connection timeout"));
        assertTrue(detailedInfo.contains("Bulk Operation Error: RuntimeException - Bulk operation failed"));
        assertTrue(detailedInfo.contains("SecurityId=" + execution.getSecurityId()));
    }

    @Test
    void testErrorRecovery_MixedScenario() {
        // Arrange - Complex scenario with multiple failure types
        List<Execution> mixedExecutions = testExecutions.subList(0, 3);
        
        // Bulk insert fails
        when(executionRepository.bulkInsert(mixedExecutions)).thenThrow(new RuntimeException("Bulk failed"));
        
        // Individual inserts: success, transient failure then success, permanent failure
        when(executionRepository.saveAndFlush(mixedExecutions.get(0))).thenReturn(mixedExecutions.get(0));
        when(executionRepository.saveAndFlush(mixedExecutions.get(1)))
            .thenThrow(new DeadlockLoserDataAccessException("Deadlock", null))
            .thenReturn(mixedExecutions.get(1));
        when(executionRepository.saveAndFlush(mixedExecutions.get(2)))
            .thenThrow(new IllegalArgumentException("Invalid data"));
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(mixedExecutions, context);
        
        // Assert
        assertEquals(2, result.size()); // 2 successful, 1 failed
        verify(executionRepository, times(1)).bulkInsert(mixedExecutions);
        verify(executionRepository, times(1)).saveAndFlush(mixedExecutions.get(0)); // 1 attempt
        verify(executionRepository, times(2)).saveAndFlush(mixedExecutions.get(1)); // 2 attempts (retry)
        verify(executionRepository, times(1)).saveAndFlush(mixedExecutions.get(2)); // 1 attempt (no retry)
        
        // Verify metrics
        verify(metrics, times(1)).recordBulkInsertFailure(3, "RuntimeException");
        verify(metrics, times(1)).recordIndividualInsertFailure("IllegalArgumentException");
        
        // Verify context state
        assertEquals(1, context.getDatabaseErrorIndices().size());
        assertEquals(2, context.getSuccessfulDatabaseIndices().size());
    }

    @Test
    void testErrorRecovery_EmptyExecutionsList() {
        // Arrange
        List<Execution> emptyList = new ArrayList<>();
        
        // Act
        List<Execution> result = errorRecoveryService.bulkInsertWithFallback(emptyList, context);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(executionRepository, never()).bulkInsert(any());
        verify(executionRepository, never()).saveAndFlush(any());
        verify(metrics, never()).recordBulkInsertSuccess(anyInt());
        verify(metrics, never()).recordBulkInsertFailure(anyInt(), anyString());
    }

    @Test
    void testErrorRecovery_NullExecutionsList() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            errorRecoveryService.bulkInsertWithFallback(null, context);
        });
    }

    private ExecutionDTO createExecutionDTO(int id) {
        return new ExecutionDTO(
            id,
            "NEW",
            "BUY",
            "NYSE",
            new SecurityDTO("AAPL" + id, "AAPL"),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.0),
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0
        );
    }
}