package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ExecutionServiceImplTest {
    @Autowired
    private ExecutionService executionService;

    @Test
    void testSaveAndFind() {
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", "SEC123456789012345678901", new BigDecimal("200.00"), new BigDecimal("20.00"), OffsetDateTime.now(), null, 1, 1);
        Execution saved = executionService.save(execution);
        Optional<Execution> found = executionService.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(new BigDecimal("200.00000000"));
    }

    @Test
    void testDeleteWithOptimisticLocking() {
        Execution execution = new Execution(null, "NEW", "SELL", "NASDAQ", "SEC123456789012345678901", new BigDecimal("75.00"), null, OffsetDateTime.now(), null, 1, 1);
        Execution saved = executionService.save(execution);
        // Should succeed
        executionService.deleteById(saved.getId(), saved.getVersion());
        // Should throw if version is wrong
        Execution another = new Execution(null, "NEW", "SELL", "NASDAQ", "SEC123456789012345678901", new BigDecimal("75.00"), null, OffsetDateTime.now(), null, 1, 1);
        Execution saved2 = executionService.save(another);
        assertThrows(OptimisticLockingFailureException.class, () -> executionService.deleteById(saved2.getId(), saved2.getVersion() + 1));
    }
} 