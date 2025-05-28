package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExecutionServiceImplTest {
    @Autowired
    private ExecutionService executionService;
    
    @MockBean
    private TradeServiceClient tradeServiceClient;

    @BeforeEach
    void setUp() {
        // Reset mocks and set up default behavior
        reset(tradeServiceClient);
        // Default behavior: return empty for version requests (simulates no trade service execution ID)
        when(tradeServiceClient.getExecutionVersion(any())).thenReturn(Optional.empty());
        when(tradeServiceClient.updateExecutionFill(any(), any())).thenReturn(false);
    }

    @Test
    void testSaveAndFind() {
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", new BigDecimal("200.00"), new BigDecimal("20.00"), OffsetDateTime.now(), null, 1, BigDecimal.ZERO, null, null);
        Execution saved = executionService.save(execution);
        Optional<Execution> found = executionService.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(new BigDecimal("200.00000000"));
    }

    @Test
    void testDeleteWithOptimisticLocking() {
        Execution execution = new Execution(null, "NEW", "SELL", "NASDAQ", "SEC123456789012345678901", new BigDecimal("75.00"), null, OffsetDateTime.now(), null, 1, BigDecimal.ZERO, null, null);
        Execution saved = executionService.save(execution);
        // Should succeed
        executionService.deleteById(saved.getId(), saved.getVersion());
        // Should throw if version is wrong
        Execution another = new Execution(null, "NEW", "SELL", "NASDAQ", "SEC123456789012345678901", new BigDecimal("75.00"), null, OffsetDateTime.now(), null, 1, BigDecimal.ZERO, null, null);
        Execution saved2 = executionService.save(another);
        assertThrows(OptimisticLockingFailureException.class, () -> executionService.deleteById(saved2.getId(), saved2.getVersion() + 1));
    }

    @Test
    void testUpdateExecution_PartialAndFull() {
        // Create execution with quantity 10
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", new BigDecimal("10.00"), new BigDecimal("1.00"), OffsetDateTime.now(), null, 1, BigDecimal.ZERO, null, null);
        Execution saved = executionService.save(execution);

        // First update: add 4, set avg price
        ExecutionPutDTO putDTO1 = new ExecutionPutDTO(new BigDecimal("4.00"), new BigDecimal("1.10"), saved.getVersion());
        Optional<Execution> updated1 = executionService.updateExecution(saved.getId(), putDTO1);
        assertThat(updated1).isPresent();
        // Re-fetch to get the latest version
        Optional<Execution> refreshed1 = executionService.findById(saved.getId());
        assertThat(refreshed1).isPresent();

        assertThat(refreshed1.get().getQuantityFilled()).isEqualTo(new BigDecimal("4.00000000"));
        assertThat(refreshed1.get().getAveragePrice()).isEqualTo(new BigDecimal("1.10000000"));
        assertThat(refreshed1.get().getExecutionStatus()).isEqualTo("PART");
        assertThat(refreshed1.get().getVersion()).isGreaterThan(saved.getVersion());
        // Second update: add 6, should become FULL
        ExecutionPutDTO putDTO2 = new ExecutionPutDTO(new BigDecimal("6.00"), new BigDecimal("1.20"), refreshed1.get().getVersion());
        Optional<Execution> updated2 = executionService.updateExecution(saved.getId(), putDTO2);
        assertThat(updated2).isPresent();
        // Re-fetch to get the latest version
        Optional<Execution> refreshed2 = executionService.findById(saved.getId());
        assertThat(refreshed2).isPresent();

        assertThat(refreshed2.get().getQuantityFilled()).isEqualTo(new BigDecimal("10.00000000"));
        assertThat(refreshed2.get().getAveragePrice()).isEqualTo(new BigDecimal("1.20000000"));
        assertThat(refreshed2.get().getExecutionStatus()).isEqualTo("FULL");
        assertThat(refreshed2.get().getVersion()).isGreaterThan(refreshed1.get().getVersion());
    }

    @Test
    void testUpdateExecution_OptimisticLocking() {
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", new BigDecimal("5.00"), new BigDecimal("1.00"), OffsetDateTime.now(), null, 1, BigDecimal.ZERO, null, null);
        Execution saved = executionService.save(execution);
        // Use wrong version
        ExecutionPutDTO putDTO = new ExecutionPutDTO(new BigDecimal("2.00"), new BigDecimal("1.05"), saved.getVersion() + 1);
        assertThrows(OptimisticLockingFailureException.class, () -> executionService.updateExecution(saved.getId(), putDTO));
    }

    @Test
    void testCreateAndSendExecution_Defaults() {
        ExecutionPostDTO postDTO = new ExecutionPostDTO("NEW", "BUY", "NYSE", "SEC123456789012345678901", new BigDecimal("7.00"), new BigDecimal("2.00"), 1, 1);
        ExecutionDTO dto = executionService.createAndSendExecution(postDTO);
        assertThat(dto.getQuantityFilled()).isEqualTo(BigDecimal.ZERO);
        assertThat(dto.getAveragePrice()).isNull();
    }

    @Test
    void testUpdateExecution_WithTradeServiceIntegration_Success() {
        // Given
        Integer tradeServiceExecutionId = 456;
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", 
                new BigDecimal("10.00"), new BigDecimal("1.00"), OffsetDateTime.now(), null, 
                tradeServiceExecutionId, BigDecimal.ZERO, null, null);
        Execution saved = executionService.save(execution);

        // Mock trade service responses
        when(tradeServiceClient.getExecutionVersion(tradeServiceExecutionId))
                .thenReturn(Optional.of(3));
        when(tradeServiceClient.updateExecutionFill(eq(tradeServiceExecutionId), any(TradeServiceExecutionFillDTO.class)))
                .thenReturn(true);

        // When
        ExecutionPutDTO putDTO = new ExecutionPutDTO(new BigDecimal("5.00"), new BigDecimal("1.15"), saved.getVersion());
        Optional<Execution> updated = executionService.updateExecution(saved.getId(), putDTO);

        // Then
        assertThat(updated).isPresent();
        assertThat(updated.get().getQuantityFilled()).isEqualTo(new BigDecimal("5.00000000"));
        assertThat(updated.get().getExecutionStatus()).isEqualTo("PART");

        // Verify trade service was called
        verify(tradeServiceClient).getExecutionVersion(tradeServiceExecutionId);
        verify(tradeServiceClient).updateExecutionFill(eq(tradeServiceExecutionId), argThat(fillDTO ->
                fillDTO.getExecutionStatus().equals("PART") &&
                fillDTO.getQuantityFilled().equals(new BigDecimal("5.00000000")) &&
                fillDTO.getVersion().equals(3)
        ));
    }

    @Test
    void testUpdateExecution_WithTradeServiceIntegration_TradeServiceFailure() {
        // Given
        Integer tradeServiceExecutionId = 789;
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", 
                new BigDecimal("10.00"), new BigDecimal("1.00"), OffsetDateTime.now(), null, 
                tradeServiceExecutionId, BigDecimal.ZERO, null, null);
        Execution saved = executionService.save(execution);

        // Mock trade service failure
        when(tradeServiceClient.getExecutionVersion(tradeServiceExecutionId))
                .thenReturn(Optional.empty());

        // When
        ExecutionPutDTO putDTO = new ExecutionPutDTO(new BigDecimal("3.00"), new BigDecimal("1.05"), saved.getVersion());
        Optional<Execution> updated = executionService.updateExecution(saved.getId(), putDTO);

        // Then - execution service should still succeed even if trade service fails
        assertThat(updated).isPresent();
        assertThat(updated.get().getQuantityFilled()).isEqualTo(new BigDecimal("3.00000000"));
        assertThat(updated.get().getExecutionStatus()).isEqualTo("PART");

        // Verify trade service was called but update was not attempted
        verify(tradeServiceClient).getExecutionVersion(tradeServiceExecutionId);
        verify(tradeServiceClient, never()).updateExecutionFill(any(), any());
    }

    @Test
    void testUpdateExecution_WithoutTradeServiceExecutionId() {
        // Given - execution without trade service ID
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", 
                new BigDecimal("10.00"), new BigDecimal("1.00"), OffsetDateTime.now(), null, 
                null, BigDecimal.ZERO, null, null); // null trade service execution ID
        Execution saved = executionService.save(execution);

        // When
        ExecutionPutDTO putDTO = new ExecutionPutDTO(new BigDecimal("2.00"), new BigDecimal("1.08"), saved.getVersion());
        Optional<Execution> updated = executionService.updateExecution(saved.getId(), putDTO);

        // Then
        assertThat(updated).isPresent();
        assertThat(updated.get().getQuantityFilled()).isEqualTo(new BigDecimal("2.00000000"));

        // Verify trade service was not called
        verify(tradeServiceClient, never()).getExecutionVersion(any());
        verify(tradeServiceClient, never()).updateExecutionFill(any(), any());
    }

    @Test
    void testUpdateExecution_TradeServiceException() {
        // Given
        Integer tradeServiceExecutionId = 999;
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", 
                new BigDecimal("10.00"), new BigDecimal("1.00"), OffsetDateTime.now(), null, 
                tradeServiceExecutionId, BigDecimal.ZERO, null, null);
        Execution saved = executionService.save(execution);

        // Mock trade service exception
        when(tradeServiceClient.getExecutionVersion(tradeServiceExecutionId))
                .thenThrow(new RuntimeException("Trade service unavailable"));

        // When
        ExecutionPutDTO putDTO = new ExecutionPutDTO(new BigDecimal("1.00"), new BigDecimal("1.02"), saved.getVersion());
        Optional<Execution> updated = executionService.updateExecution(saved.getId(), putDTO);

        // Then - execution service should still succeed
        assertThat(updated).isPresent();
        assertThat(updated.get().getQuantityFilled()).isEqualTo(new BigDecimal("1.00000000"));

        // Verify trade service was called
        verify(tradeServiceClient).getExecutionVersion(tradeServiceExecutionId);
    }
} 