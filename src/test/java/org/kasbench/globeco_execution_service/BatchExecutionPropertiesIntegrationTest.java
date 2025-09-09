package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for BatchExecutionProperties to verify Spring Boot configuration loading.
 */
@SpringBootTest(classes = {BatchExecutionProperties.class})
@EnableConfigurationProperties(BatchExecutionProperties.class)
@TestPropertySource(properties = {
    "batch.execution.bulk-insert-batch-size=750",
    "batch.execution.max-concurrent-batches=15",
    "batch.execution.enable-async-kafka=false",
    "batch.execution.kafka.max-attempts=5",
    "batch.execution.kafka.initial-delay=2000",
    "batch.execution.database.max-pool-size=25",
    "batch.execution.performance.enable-dynamic-batch-sizing=true"
})
class BatchExecutionPropertiesIntegrationTest {

    @Autowired
    private BatchExecutionProperties batchExecutionProperties;

    @Test
    void testConfigurationPropertiesLoading() {
        // Verify that properties are loaded correctly from application context
        assertNotNull(batchExecutionProperties);
        
        // Test main properties
        assertEquals(750, batchExecutionProperties.getBulkInsertBatchSize());
        assertEquals(15, batchExecutionProperties.getMaxConcurrentBatches());
        assertFalse(batchExecutionProperties.isEnableAsyncKafka());
        
        // Test nested Kafka properties
        assertEquals(5, batchExecutionProperties.getKafka().getMaxAttempts());
        assertEquals(2000, batchExecutionProperties.getKafka().getInitialDelay());
        
        // Test nested Database properties
        assertEquals(25, batchExecutionProperties.getDatabase().getMaxPoolSize());
        
        // Test nested Performance properties
        assertTrue(batchExecutionProperties.getPerformance().isEnableDynamicBatchSizing());
    }

    @Test
    void testDefaultValuesWhenNotOverridden() {
        // Test that non-overridden properties still have their defaults
        assertEquals(2.0, batchExecutionProperties.getKafka().getBackoffMultiplier());
        assertEquals(30000, batchExecutionProperties.getKafka().getMaxDelay());
        assertTrue(batchExecutionProperties.getKafka().isEnableDeadLetterQueue());
        
        assertEquals(30000, batchExecutionProperties.getDatabase().getConnectionTimeout());
        assertEquals(1800000, batchExecutionProperties.getDatabase().getMaxLifetime());
        
        assertEquals(50, batchExecutionProperties.getPerformance().getMinBatchSize());
        assertEquals(2000, batchExecutionProperties.getPerformance().getMaxBatchSize());
        assertEquals(5, batchExecutionProperties.getPerformance().getCircuitBreakerFailureThreshold());
        assertEquals(60000, batchExecutionProperties.getPerformance().getCircuitBreakerRecoveryTimeout());
    }

    @Test
    void testNestedPropertiesAreNotNull() {
        // Verify that all nested properties are properly initialized
        assertNotNull(batchExecutionProperties.getKafka());
        assertNotNull(batchExecutionProperties.getDatabase());
        assertNotNull(batchExecutionProperties.getPerformance());
    }
}