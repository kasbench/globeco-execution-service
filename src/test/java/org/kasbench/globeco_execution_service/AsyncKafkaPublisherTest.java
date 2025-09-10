package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AsyncKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, ExecutionDTO> kafkaTemplate;

    @Mock
    private SendResult<String, ExecutionDTO> sendResult;

    private BatchExecutionProperties batchProperties;
    private AsyncKafkaPublisher publisher;
    private ExecutionDTO testExecution;

    @BeforeEach
    void setUp() {
        batchProperties = new BatchExecutionProperties();
        batchProperties.setEnableAsyncKafka(true);
        
        // Configure retry properties
        BatchExecutionProperties.KafkaRetryProperties kafkaProps = new BatchExecutionProperties.KafkaRetryProperties();
        kafkaProps.setMaxAttempts(3);
        kafkaProps.setInitialDelay(100);
        kafkaProps.setBackoffMultiplier(2.0);
        kafkaProps.setMaxDelay(1000);
        kafkaProps.setEnableDeadLetterQueue(true);
        batchProperties.setKafka(kafkaProps);
        
        // Configure performance properties
        BatchExecutionProperties.PerformanceProperties perfProps = new BatchExecutionProperties.PerformanceProperties();
        perfProps.setCircuitBreakerFailureThreshold(3);
        perfProps.setCircuitBreakerRecoveryTimeout(5000);
        batchProperties.setPerformance(perfProps);

        BatchProcessingMetrics mockMetrics = mock(BatchProcessingMetrics.class);
        publisher = new AsyncKafkaPublisher(kafkaTemplate, batchProperties, mockMetrics, "test-topic");

        // Create test execution
        testExecution = new ExecutionDTO(
                1,
                "NEW",
                "BUY",
                "NYSE",
                new SecurityDTO("AAPL", "Apple Inc."),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(150.00),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                123,
                BigDecimal.ZERO,
                null,
                1
        );
    }

    @Test
    void testPublishAsync_Success() throws Exception {
        // Arrange
        CompletableFuture<SendResult<String, ExecutionDTO>> successFuture = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq("test-topic"), eq("1"), eq(testExecution))).thenReturn(successFuture);

        // Act
        CompletableFuture<AsyncKafkaPublisher.PublishResult> result = publisher.publishAsync(testExecution);
        AsyncKafkaPublisher.PublishResult publishResult = result.get(5, TimeUnit.SECONDS);

        // Assert
        assertTrue(publishResult.isSuccess());
        assertFalse(publishResult.isSkipped());
        assertEquals(Integer.valueOf(1), publishResult.getExecutionId());
        assertEquals(1, publishResult.getAttemptCount());
        assertNull(publishResult.getErrorMessage());

        verify(kafkaTemplate).send("test-topic", "1", testExecution);
        
        // Check metrics
        AsyncKafkaPublisher.PublishMetrics metrics = publisher.getMetrics();
        assertEquals(1, metrics.getTotalAttempts());
        assertEquals(1, metrics.getSuccessfulPublishes());
        assertEquals(0, metrics.getFailedPublishes());
    }

    @Test
    void testPublishAsync_Disabled() throws Exception {
        // Arrange
        batchProperties.setEnableAsyncKafka(false);

        // Act
        CompletableFuture<AsyncKafkaPublisher.PublishResult> result = publisher.publishAsync(testExecution);
        AsyncKafkaPublisher.PublishResult publishResult = result.get(1, TimeUnit.SECONDS);

        // Assert
        assertFalse(publishResult.isSuccess());
        assertTrue(publishResult.isSkipped());
        assertEquals(Integer.valueOf(1), publishResult.getExecutionId());
        assertEquals(0, publishResult.getAttemptCount());
        assertEquals("Async Kafka disabled", publishResult.getErrorMessage());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void testPublishAsync_RetryOnFailure() throws Exception {
        // Arrange
        CompletableFuture<SendResult<String, ExecutionDTO>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        
        CompletableFuture<SendResult<String, ExecutionDTO>> successFuture = CompletableFuture.completedFuture(sendResult);
        
        when(kafkaTemplate.send(eq("test-topic"), eq("1"), eq(testExecution)))
                .thenReturn(failedFuture)
                .thenReturn(successFuture);

        // Act
        CompletableFuture<AsyncKafkaPublisher.PublishResult> result = publisher.publishAsync(testExecution);
        AsyncKafkaPublisher.PublishResult publishResult = result.get(10, TimeUnit.SECONDS);

        // Assert
        assertTrue(publishResult.isSuccess());
        assertEquals(Integer.valueOf(1), publishResult.getExecutionId());
        assertEquals(2, publishResult.getAttemptCount()); // First attempt + 1 retry

        verify(kafkaTemplate, times(2)).send("test-topic", "1", testExecution);
        
        // Check metrics
        AsyncKafkaPublisher.PublishMetrics metrics = publisher.getMetrics();
        assertEquals(2, metrics.getTotalAttempts());
        assertEquals(1, metrics.getSuccessfulPublishes());
        assertEquals(0, metrics.getFailedPublishes()); // Final result was success
        assertEquals(1, metrics.getRetriedPublishes());
    }

    @Test
    void testPublishAsync_MaxRetriesExceeded() throws Exception {
        // Arrange
        CompletableFuture<SendResult<String, ExecutionDTO>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Persistent failure"));
        
        when(kafkaTemplate.send(eq("test-topic"), eq("1"), eq(testExecution)))
                .thenReturn(failedFuture);
        when(kafkaTemplate.send(eq("test-topic.dlq"), eq("1"), eq(testExecution)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act
        CompletableFuture<AsyncKafkaPublisher.PublishResult> result = publisher.publishAsync(testExecution);
        AsyncKafkaPublisher.PublishResult publishResult = result.get(15, TimeUnit.SECONDS);

        // Assert
        assertFalse(publishResult.isSuccess());
        assertFalse(publishResult.isSkipped());
        assertEquals(Integer.valueOf(1), publishResult.getExecutionId());
        assertEquals(3, publishResult.getAttemptCount()); // 3 attempts total
        assertEquals("Persistent failure", publishResult.getErrorMessage());

        // Verify original topic was called 3 times (initial + 2 retries)
        verify(kafkaTemplate, times(3)).send("test-topic", "1", testExecution);
        // Verify DLQ was called once
        verify(kafkaTemplate, times(1)).send("test-topic.dlq", "1", testExecution);
        
        // Check metrics
        AsyncKafkaPublisher.PublishMetrics metrics = publisher.getMetrics();
        assertEquals(3, metrics.getTotalAttempts());
        assertEquals(0, metrics.getSuccessfulPublishes());
        assertEquals(1, metrics.getFailedPublishes());
        assertEquals(2, metrics.getRetriedPublishes());
        assertEquals(1, metrics.getDeadLetterMessages());
    }

    @Test
    void testPublishBatchAsync_Success() throws Exception {
        // Arrange
        ExecutionDTO execution2 = new ExecutionDTO(
                2, "NEW", "SELL", "NASDAQ", new SecurityDTO("GOOGL", "Alphabet Inc."),
                BigDecimal.valueOf(50), BigDecimal.valueOf(2800.00),
                OffsetDateTime.now(), OffsetDateTime.now(), 124,
                BigDecimal.ZERO, null, 1
        );
        
        List<ExecutionDTO> executions = List.of(testExecution, execution2);
        
        CompletableFuture<SendResult<String, ExecutionDTO>> successFuture = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any(ExecutionDTO.class))).thenReturn(successFuture);

        // Act
        CompletableFuture<AsyncKafkaPublisher.BatchPublishResult> result = publisher.publishBatchAsync(executions);
        AsyncKafkaPublisher.BatchPublishResult batchResult = result.get(5, TimeUnit.SECONDS);

        // Assert
        assertEquals(2, batchResult.getTotalMessages());
        assertEquals(2, batchResult.getSuccessfulMessages());
        assertEquals(0, batchResult.getFailedMessages());
        assertEquals(0, batchResult.getSkippedMessages());

        verify(kafkaTemplate).send("test-topic", "1", testExecution);
        verify(kafkaTemplate).send("test-topic", "2", execution2);
    }

    @Test
    void testPublishBatchAsync_Disabled() throws Exception {
        // Arrange
        batchProperties.setEnableAsyncKafka(false);
        List<ExecutionDTO> executions = List.of(testExecution);

        // Act
        CompletableFuture<AsyncKafkaPublisher.BatchPublishResult> result = publisher.publishBatchAsync(executions);
        AsyncKafkaPublisher.BatchPublishResult batchResult = result.get(1, TimeUnit.SECONDS);

        // Assert
        assertEquals(1, batchResult.getTotalMessages());
        assertEquals(0, batchResult.getSuccessfulMessages());
        assertEquals(0, batchResult.getFailedMessages());
        assertEquals(1, batchResult.getSkippedMessages());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void testCircuitBreaker_OpensAfterFailures() throws Exception {
        // Arrange
        CompletableFuture<SendResult<String, ExecutionDTO>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Service unavailable"));
        
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any(ExecutionDTO.class)))
                .thenReturn(failedFuture);
        lenient().when(kafkaTemplate.send(eq("test-topic.dlq"), anyString(), any(ExecutionDTO.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act - trigger enough failures to open circuit breaker
        for (int i = 0; i < 3; i++) {
            ExecutionDTO execution = new ExecutionDTO(
                    i + 1, "NEW", "BUY", "NYSE", new SecurityDTO("TEST", "Test"),
                    BigDecimal.valueOf(100), BigDecimal.valueOf(100.00),
                    OffsetDateTime.now(), OffsetDateTime.now(), 100 + i,
                    BigDecimal.ZERO, null, 1
            );
            
            CompletableFuture<AsyncKafkaPublisher.PublishResult> result = publisher.publishAsync(execution);
            AsyncKafkaPublisher.PublishResult publishResult = result.get(15, TimeUnit.SECONDS);
            assertFalse(publishResult.isSuccess());
        }

        // Circuit should now be open - next request should fail fast
        CompletableFuture<AsyncKafkaPublisher.PublishResult> result = publisher.publishAsync(testExecution);
        AsyncKafkaPublisher.PublishResult publishResult = result.get(1, TimeUnit.SECONDS);

        // Assert
        assertFalse(publishResult.isSuccess());
        assertEquals("Circuit breaker is open", publishResult.getErrorMessage());
        
        // Check metrics
        AsyncKafkaPublisher.PublishMetrics metrics = publisher.getMetrics();
        assertEquals(AsyncKafkaPublisher.CircuitBreakerState.OPEN, metrics.getCircuitState());
        assertEquals(3, metrics.getCurrentFailureCount());
    }

    @Test
    void testCircuitBreaker_Reset() {
        // Arrange - open the circuit breaker first
        CompletableFuture<SendResult<String, ExecutionDTO>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Service unavailable"));
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any(ExecutionDTO.class))).thenReturn(failedFuture);
        lenient().when(kafkaTemplate.send(eq("test-topic.dlq"), anyString(), any(ExecutionDTO.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Trigger failures to open circuit
        for (int i = 0; i < 3; i++) {
            try {
                publisher.publishAsync(testExecution).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Expected
            }
        }

        // Verify circuit is open
        AsyncKafkaPublisher.PublishMetrics metrics = publisher.getMetrics();
        assertEquals(AsyncKafkaPublisher.CircuitBreakerState.OPEN, metrics.getCircuitState());

        // Act - reset circuit breaker
        publisher.resetCircuitBreaker();

        // Assert
        metrics = publisher.getMetrics();
        assertEquals(AsyncKafkaPublisher.CircuitBreakerState.CLOSED, metrics.getCircuitState());
        assertEquals(0, metrics.getCurrentFailureCount());
    }

    @Test
    void testMetrics_Calculation() throws Exception {
        // Arrange
        CompletableFuture<SendResult<String, ExecutionDTO>> successFuture = CompletableFuture.completedFuture(sendResult);
        CompletableFuture<SendResult<String, ExecutionDTO>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Test failure"));
        
        when(kafkaTemplate.send(eq("test-topic"), eq("1"), any(ExecutionDTO.class))).thenReturn(successFuture);
        when(kafkaTemplate.send(eq("test-topic"), eq("2"), any(ExecutionDTO.class))).thenReturn(failedFuture);
        when(kafkaTemplate.send(eq("test-topic.dlq"), anyString(), any(ExecutionDTO.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act - one success, one failure
        publisher.publishAsync(testExecution).get(5, TimeUnit.SECONDS);
        
        ExecutionDTO execution2 = new ExecutionDTO(
                2, "NEW", "SELL", "NASDAQ", new SecurityDTO("GOOGL", "Alphabet Inc."),
                BigDecimal.valueOf(50), BigDecimal.valueOf(2800.00),
                OffsetDateTime.now(), OffsetDateTime.now(), 124,
                BigDecimal.ZERO, null, 1
        );
        publisher.publishAsync(execution2).get(15, TimeUnit.SECONDS);

        // Assert
        AsyncKafkaPublisher.PublishMetrics metrics = publisher.getMetrics();
        assertEquals(4, metrics.getTotalAttempts()); // 1 success + 3 failed attempts
        assertEquals(1, metrics.getSuccessfulPublishes());
        assertEquals(1, metrics.getFailedPublishes());
        assertEquals(2, metrics.getRetriedPublishes()); // 2 retries for the failed message
        assertEquals(1, metrics.getDeadLetterMessages());
        assertEquals(0.25, metrics.getSuccessRate(), 0.01); // 1 success out of 4 attempts
    }
}