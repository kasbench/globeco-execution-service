package org.kasbench.globeco_execution_service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for batch execution processing.
 * Provides configurable parameters for bulk operations, retry policies, and performance tuning.
 */
@Component
@ConfigurationProperties(prefix = "batch.execution")
@Validated
public class BatchExecutionProperties {

    /**
     * Maximum number of executions to process in a single bulk database operation.
     * Default: 500
     */
    @Min(value = 1, message = "Bulk insert batch size must be at least 1")
    @Max(value = 10000, message = "Bulk insert batch size cannot exceed 10000")
    private int bulkInsertBatchSize = 500;

    /**
     * Maximum number of concurrent batch requests that can be processed simultaneously.
     * Default: 10
     */
    @Min(value = 1, message = "Max concurrent batches must be at least 1")
    @Max(value = 100, message = "Max concurrent batches cannot exceed 100")
    private int maxConcurrentBatches = 10;

    /**
     * Whether to enable asynchronous Kafka message publishing.
     * Default: true
     */
    private boolean enableAsyncKafka = true;

    /**
     * Database connection pool settings for bulk operations.
     */
    @Valid
    @NotNull
    private DatabaseProperties database = new DatabaseProperties();

    /**
     * Kafka retry configuration for message publishing.
     */
    @Valid
    @NotNull
    private KafkaRetryProperties kafka = new KafkaRetryProperties();

    /**
     * Performance tuning parameters.
     */
    @Valid
    @NotNull
    private PerformanceProperties performance = new PerformanceProperties();

    // Getters and setters
    public int getBulkInsertBatchSize() {
        return bulkInsertBatchSize;
    }

    public void setBulkInsertBatchSize(int bulkInsertBatchSize) {
        this.bulkInsertBatchSize = bulkInsertBatchSize;
    }

    public int getMaxConcurrentBatches() {
        return maxConcurrentBatches;
    }

    public void setMaxConcurrentBatches(int maxConcurrentBatches) {
        this.maxConcurrentBatches = maxConcurrentBatches;
    }

    public boolean isEnableAsyncKafka() {
        return enableAsyncKafka;
    }

    public void setEnableAsyncKafka(boolean enableAsyncKafka) {
        this.enableAsyncKafka = enableAsyncKafka;
    }

    public DatabaseProperties getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseProperties database) {
        this.database = database;
    }

    public KafkaRetryProperties getKafka() {
        return kafka;
    }

    public void setKafka(KafkaRetryProperties kafka) {
        this.kafka = kafka;
    }

    public PerformanceProperties getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceProperties performance) {
        this.performance = performance;
    }

    /**
     * Kafka retry configuration properties.
     */
    public static class KafkaRetryProperties {
        /**
         * Maximum number of retry attempts for failed Kafka messages.
         * Default: 3
         */
        @Min(value = 0, message = "Max retry attempts cannot be negative")
        @Max(value = 10, message = "Max retry attempts cannot exceed 10")
        private int maxAttempts = 3;

        /**
         * Initial delay in milliseconds before first retry attempt.
         * Default: 1000ms (1 second)
         */
        @Min(value = 100, message = "Initial delay must be at least 100ms")
        @Max(value = 60000, message = "Initial delay cannot exceed 60 seconds")
        private long initialDelay = 1000;

        /**
         * Multiplier for exponential backoff between retry attempts.
         * Default: 2.0
         */
        @Min(value = 1, message = "Backoff multiplier must be at least 1.0")
        @Max(value = 10, message = "Backoff multiplier cannot exceed 10.0")
        private double backoffMultiplier = 2.0;

        /**
         * Maximum delay in milliseconds between retry attempts.
         * Default: 30000ms (30 seconds)
         */
        @Min(value = 1000, message = "Max delay must be at least 1 second")
        @Max(value = 300000, message = "Max delay cannot exceed 5 minutes")
        private long maxDelay = 30000;

        /**
         * Whether to enable dead letter queue for permanently failed messages.
         * Default: true
         */
        private boolean enableDeadLetterQueue = true;

        // Getters and setters
        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(long initialDelay) {
            this.initialDelay = initialDelay;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public long getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(long maxDelay) {
            this.maxDelay = maxDelay;
        }

        public boolean isEnableDeadLetterQueue() {
            return enableDeadLetterQueue;
        }

        public void setEnableDeadLetterQueue(boolean enableDeadLetterQueue) {
            this.enableDeadLetterQueue = enableDeadLetterQueue;
        }
    }

    /**
     * Database connection pool configuration for bulk operations.
     */
    public static class DatabaseProperties {
        /**
         * Maximum pool size for database connections during bulk operations.
         * Default: 20
         */
        @Min(value = 1, message = "Max pool size must be at least 1")
        @Max(value = 100, message = "Max pool size cannot exceed 100")
        private int maxPoolSize = 20;

        /**
         * Connection timeout in milliseconds for bulk operations.
         * Default: 30000ms (30 seconds)
         */
        @Min(value = 1000, message = "Connection timeout must be at least 1 second")
        @Max(value = 300000, message = "Connection timeout cannot exceed 5 minutes")
        private long connectionTimeout = 30000;

        /**
         * Maximum lifetime of a connection in milliseconds.
         * Default: 1800000ms (30 minutes)
         */
        @Min(value = 60000, message = "Max lifetime must be at least 1 minute")
        @Max(value = 7200000, message = "Max lifetime cannot exceed 2 hours")
        private long maxLifetime = 1800000;

        /**
         * Maximum number of retry attempts for transient database errors.
         * Default: 3
         */
        @Min(value = 0, message = "Max retries cannot be negative")
        @Max(value = 10, message = "Max retries cannot exceed 10")
        private int maxRetries = 3;

        /**
         * Initial delay in milliseconds before first retry attempt for database operations.
         * Default: 500ms
         */
        @Min(value = 100, message = "Retry delay must be at least 100ms")
        @Max(value = 10000, message = "Retry delay cannot exceed 10 seconds")
        private long retryDelayMs = 500;

        /**
         * Maximum delay in milliseconds between database retry attempts.
         * Default: 5000ms (5 seconds)
         */
        @Min(value = 1000, message = "Max retry delay must be at least 1 second")
        @Max(value = 60000, message = "Max retry delay cannot exceed 1 minute")
        private long maxRetryDelayMs = 5000;

        // Getters and setters
        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryDelayMs() {
            return retryDelayMs;
        }

        public void setRetryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
        }

        public long getMaxRetryDelayMs() {
            return maxRetryDelayMs;
        }

        public void setMaxRetryDelayMs(long maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
        }
    }

    /**
     * Performance tuning configuration properties.
     */
    public static class PerformanceProperties {
        /**
         * Whether to enable dynamic batch size adjustment based on system performance.
         * Default: false
         */
        private boolean enableDynamicBatchSizing = false;

        /**
         * Minimum batch size when dynamic sizing is enabled.
         * Default: 50
         */
        @Min(value = 1, message = "Min batch size must be at least 1")
        @Max(value = 1000, message = "Min batch size cannot exceed 1000")
        private int minBatchSize = 50;

        /**
         * Maximum batch size when dynamic sizing is enabled.
         * Default: 2000
         */
        @Min(value = 100, message = "Max batch size must be at least 100")
        @Max(value = 10000, message = "Max batch size cannot exceed 10000")
        private int maxBatchSize = 2000;

        /**
         * Circuit breaker failure threshold for Kafka operations.
         * Default: 5
         */
        @Min(value = 1, message = "Circuit breaker threshold must be at least 1")
        @Max(value = 50, message = "Circuit breaker threshold cannot exceed 50")
        private int circuitBreakerFailureThreshold = 5;

        /**
         * Circuit breaker recovery timeout in milliseconds.
         * Default: 60000ms (1 minute)
         */
        @Min(value = 10000, message = "Circuit breaker timeout must be at least 10 seconds")
        @Max(value = 600000, message = "Circuit breaker timeout cannot exceed 10 minutes")
        private long circuitBreakerRecoveryTimeout = 60000;

        // Getters and setters
        public boolean isEnableDynamicBatchSizing() {
            return enableDynamicBatchSizing;
        }

        public void setEnableDynamicBatchSizing(boolean enableDynamicBatchSizing) {
            this.enableDynamicBatchSizing = enableDynamicBatchSizing;
        }

        public int getMinBatchSize() {
            return minBatchSize;
        }

        public void setMinBatchSize(int minBatchSize) {
            this.minBatchSize = minBatchSize;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }

        public int getCircuitBreakerFailureThreshold() {
            return circuitBreakerFailureThreshold;
        }

        public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
            this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
        }

        public long getCircuitBreakerRecoveryTimeout() {
            return circuitBreakerRecoveryTimeout;
        }

        public void setCircuitBreakerRecoveryTimeout(long circuitBreakerRecoveryTimeout) {
            this.circuitBreakerRecoveryTimeout = circuitBreakerRecoveryTimeout;
        }
    }
}