package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BatchExecutionProperties configuration class.
 * Tests configuration loading, validation, and default values.
 */
class BatchExecutionPropertiesTest {

    private Validator validator;
    private BatchExecutionProperties properties;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        properties = new BatchExecutionProperties();
    }

    @Test
    void testDefaultValues() {
        // Test main properties defaults
        assertEquals(500, properties.getBulkInsertBatchSize());
        assertEquals(10, properties.getMaxConcurrentBatches());
        assertTrue(properties.isEnableAsyncKafka());

        // Test Kafka retry defaults
        BatchExecutionProperties.KafkaRetryProperties kafka = properties.getKafka();
        assertEquals(3, kafka.getMaxAttempts());
        assertEquals(1000, kafka.getInitialDelay());
        assertEquals(2.0, kafka.getBackoffMultiplier());
        assertEquals(30000, kafka.getMaxDelay());
        assertTrue(kafka.isEnableDeadLetterQueue());

        // Test database defaults
        BatchExecutionProperties.DatabaseProperties database = properties.getDatabase();
        assertEquals(20, database.getMaxPoolSize());
        assertEquals(30000, database.getConnectionTimeout());
        assertEquals(1800000, database.getMaxLifetime());

        // Test performance defaults
        BatchExecutionProperties.PerformanceProperties performance = properties.getPerformance();
        assertFalse(performance.isEnableDynamicBatchSizing());
        assertEquals(50, performance.getMinBatchSize());
        assertEquals(2000, performance.getMaxBatchSize());
        assertEquals(5, performance.getCircuitBreakerFailureThreshold());
        assertEquals(60000, performance.getCircuitBreakerRecoveryTimeout());
    }

    @Test
    void testValidConfiguration() {
        // Test with valid values
        properties.setBulkInsertBatchSize(1000);
        properties.setMaxConcurrentBatches(20);
        properties.setEnableAsyncKafka(false);

        Set<ConstraintViolation<BatchExecutionProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "Valid configuration should not have violations");
    }

    @Test
    void testBulkInsertBatchSizeValidation() {
        // Test minimum boundary
        properties.setBulkInsertBatchSize(0);
        Set<ConstraintViolation<BatchExecutionProperties>> violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1"));

        // Test maximum boundary
        properties.setBulkInsertBatchSize(10001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 10000"));

        // Test valid boundary values
        properties.setBulkInsertBatchSize(1);
        violations = validator.validate(properties);
        assertTrue(violations.isEmpty());

        properties.setBulkInsertBatchSize(10000);
        violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testMaxConcurrentBatchesValidation() {
        // Test minimum boundary
        properties.setMaxConcurrentBatches(0);
        Set<ConstraintViolation<BatchExecutionProperties>> violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1"));

        // Test maximum boundary
        properties.setMaxConcurrentBatches(101);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 100"));
    }

    @Test
    void testKafkaRetryPropertiesValidation() {
        BatchExecutionProperties.KafkaRetryProperties kafka = properties.getKafka();

        // Test max attempts validation
        kafka.setMaxAttempts(-1);
        Set<ConstraintViolation<BatchExecutionProperties>> violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot be negative"));

        kafka.setMaxAttempts(11);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 10"));

        // Test initial delay validation
        kafka.setMaxAttempts(3); // Reset to valid value
        kafka.setInitialDelay(50);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 100ms"));

        kafka.setInitialDelay(60001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 60 seconds"));

        // Test backoff multiplier validation
        kafka.setInitialDelay(1000); // Reset to valid value
        kafka.setBackoffMultiplier(0.5);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1.0"));

        kafka.setBackoffMultiplier(11.0);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 10.0"));

        // Test max delay validation
        kafka.setBackoffMultiplier(2.0); // Reset to valid value
        kafka.setMaxDelay(500);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1 second"));

        kafka.setMaxDelay(300001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 5 minutes"));
    }

    @Test
    void testDatabasePropertiesValidation() {
        BatchExecutionProperties.DatabaseProperties database = properties.getDatabase();

        // Test max pool size validation
        database.setMaxPoolSize(0);
        Set<ConstraintViolation<BatchExecutionProperties>> violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1"));

        database.setMaxPoolSize(101);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 100"));

        // Test connection timeout validation
        database.setMaxPoolSize(20); // Reset to valid value
        database.setConnectionTimeout(500);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1 second"));

        database.setConnectionTimeout(300001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 5 minutes"));

        // Test max lifetime validation
        database.setConnectionTimeout(30000); // Reset to valid value
        database.setMaxLifetime(30000);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1 minute"));

        database.setMaxLifetime(7200001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 2 hours"));
    }

    @Test
    void testPerformancePropertiesValidation() {
        BatchExecutionProperties.PerformanceProperties performance = properties.getPerformance();

        // Test min batch size validation
        performance.setMinBatchSize(0);
        Set<ConstraintViolation<BatchExecutionProperties>> violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1"));

        performance.setMinBatchSize(1001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 1000"));

        // Test max batch size validation
        performance.setMinBatchSize(50); // Reset to valid value
        performance.setMaxBatchSize(50);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 100"));

        performance.setMaxBatchSize(10001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 10000"));

        // Test circuit breaker threshold validation
        performance.setMaxBatchSize(2000); // Reset to valid value
        performance.setCircuitBreakerFailureThreshold(0);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 1"));

        performance.setCircuitBreakerFailureThreshold(51);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 50"));

        // Test circuit breaker recovery timeout validation
        performance.setCircuitBreakerFailureThreshold(5); // Reset to valid value
        performance.setCircuitBreakerRecoveryTimeout(5000);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must be at least 10 seconds"));

        performance.setCircuitBreakerRecoveryTimeout(600001);
        violations = validator.validate(properties);
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("cannot exceed 10 minutes"));
    }

    @Test
    void testConfigurationBinding() {
        // Test configuration binding from properties
        Map<String, Object> properties = new HashMap<>();
        properties.put("batch.execution.bulk-insert-batch-size", "1000");
        properties.put("batch.execution.max-concurrent-batches", "20");
        properties.put("batch.execution.enable-async-kafka", "false");
        properties.put("batch.execution.kafka.max-attempts", "5");
        properties.put("batch.execution.kafka.initial-delay", "2000");
        properties.put("batch.execution.database.max-pool-size", "30");
        properties.put("batch.execution.performance.enable-dynamic-batch-sizing", "true");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        
        BindResult<BatchExecutionProperties> result = binder.bind("batch.execution", BatchExecutionProperties.class);
        
        assertTrue(result.isBound());
        BatchExecutionProperties boundProperties = result.get();
        
        assertEquals(1000, boundProperties.getBulkInsertBatchSize());
        assertEquals(20, boundProperties.getMaxConcurrentBatches());
        assertFalse(boundProperties.isEnableAsyncKafka());
        assertEquals(5, boundProperties.getKafka().getMaxAttempts());
        assertEquals(2000, boundProperties.getKafka().getInitialDelay());
        assertEquals(30, boundProperties.getDatabase().getMaxPoolSize());
        assertTrue(boundProperties.getPerformance().isEnableDynamicBatchSizing());
    }

    @Test
    void testNestedPropertiesNotNull() {
        // Test that nested properties are not null by default
        assertNotNull(properties.getKafka());
        assertNotNull(properties.getDatabase());
        assertNotNull(properties.getPerformance());
    }

    @Test
    void testSettersAndGetters() {
        // Test all setters and getters work correctly
        properties.setBulkInsertBatchSize(750);
        assertEquals(750, properties.getBulkInsertBatchSize());

        properties.setMaxConcurrentBatches(15);
        assertEquals(15, properties.getMaxConcurrentBatches());

        properties.setEnableAsyncKafka(false);
        assertFalse(properties.isEnableAsyncKafka());

        // Test nested properties setters/getters
        BatchExecutionProperties.KafkaRetryProperties kafka = new BatchExecutionProperties.KafkaRetryProperties();
        kafka.setMaxAttempts(5);
        properties.setKafka(kafka);
        assertEquals(5, properties.getKafka().getMaxAttempts());

        BatchExecutionProperties.DatabaseProperties database = new BatchExecutionProperties.DatabaseProperties();
        database.setMaxPoolSize(25);
        properties.setDatabase(database);
        assertEquals(25, properties.getDatabase().getMaxPoolSize());

        BatchExecutionProperties.PerformanceProperties performance = new BatchExecutionProperties.PerformanceProperties();
        performance.setEnableDynamicBatchSizing(true);
        properties.setPerformance(performance);
        assertTrue(properties.getPerformance().isEnableDynamicBatchSizing());
    }

    @Test
    void testProductionReadyDefaults() {
        // Verify that default values are suitable for production use
        assertTrue(properties.getBulkInsertBatchSize() > 0 && properties.getBulkInsertBatchSize() <= 1000,
                "Bulk insert batch size should be reasonable for production");
        
        assertTrue(properties.getMaxConcurrentBatches() > 0 && properties.getMaxConcurrentBatches() <= 50,
                "Max concurrent batches should be reasonable for production");
        
        assertTrue(properties.getKafka().getMaxAttempts() >= 3,
                "Kafka retry attempts should be sufficient for production resilience");
        
        assertTrue(properties.getDatabase().getMaxPoolSize() >= 10,
                "Database pool size should be sufficient for production load");
        
        assertTrue(properties.getPerformance().getCircuitBreakerFailureThreshold() >= 3,
                "Circuit breaker threshold should allow for transient failures");
    }
}