package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ExecutionCacheTest {
    @Autowired
    private ExecutionService executionService;

    @Autowired
    private CacheManager cacheManager;

    @SuppressWarnings("null")
    @Test
    void testFindByIdCaching() {
        Execution execution = new Execution(null, "CACHED", "BUY", "LSE", new BigDecimal("10.00"), null, OffsetDateTime.now(), null, null);
        Execution saved = executionService.save(execution);
        @SuppressWarnings("unused")
        String cacheKey = String.valueOf(saved.getId());

        // First call - should load from DB
        Optional<Execution> found1 = executionService.findById(saved.getId());
        assertThat(found1).isPresent();

        // Second call - should hit cache
        Optional<Execution> found2 = executionService.findById(saved.getId());
        assertThat(found2).isPresent();

        // Check cache directly
        Object cached = cacheManager.getCache("execution").get(saved.getId(), Execution.class);
        assertThat(cached).isNotNull();
        assertThat(((Execution) cached).getId()).isEqualTo(saved.getId());
    }

    @SuppressWarnings("null")
    @Test
    void testFindAllCaching() {
        executionService.save(new Execution(null, "CACHED", "SELL", "LSE", new BigDecimal("20.00"), null, OffsetDateTime.now(), null, null));
        // First call - should load from DB
        executionService.findAll();
        // Second call - should hit cache
        Object cached = cacheManager.getCache("execution").get("all", java.util.List.class);
        assertThat(cached).isNotNull();
    }
} 