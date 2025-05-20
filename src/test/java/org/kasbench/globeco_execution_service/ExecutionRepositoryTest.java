package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ExecutionRepositoryTest {
    @Autowired
    private ExecutionRepository executionRepository;

    @Test
    void testSaveAndFindById() {
        Execution execution = new Execution(null, "NEW", "BUY", "NYSE", new BigDecimal("100.00"), new BigDecimal("10.50"), OffsetDateTime.now(), null, 1);
        Execution saved = executionRepository.save(execution);
        Optional<Execution> found = executionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getExecutionStatus()).isEqualTo("NEW");
    }

    @Test
    void testDelete() {
        Execution execution = new Execution(null, "NEW", "SELL", "NASDAQ", new BigDecimal("50.00"), null, OffsetDateTime.now(), null, 1);
        Execution saved = executionRepository.save(execution);
        executionRepository.deleteById(saved.getId());
        assertThat(executionRepository.findById(saved.getId())).isNotPresent();
    }
} 