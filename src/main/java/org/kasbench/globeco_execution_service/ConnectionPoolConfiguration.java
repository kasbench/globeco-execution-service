package org.kasbench.globeco_execution_service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration class for optimizing HikariCP connection pool settings for bulk operations.
 * Provides enhanced connection pool configuration with monitoring and performance tuning.
 */
@Configuration
public class ConnectionPoolConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolConfiguration.class);

    @Autowired
    private BatchExecutionProperties batchExecutionProperties;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Creates an optimized HikariCP DataSource for bulk operations.
     * Configures connection pool settings based on batch processing requirements.
     */
    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        HikariConfig config = new HikariConfig();
        
        // Basic connection settings
        config.setJdbcUrl(dataSourceProperties.getUrl());
        config.setUsername(dataSourceProperties.getUsername());
        config.setPassword(dataSourceProperties.getPassword());
        config.setDriverClassName(dataSourceProperties.getDriverClassName());
        
        // Optimized pool settings for bulk operations
        BatchExecutionProperties.DatabaseProperties dbProps = batchExecutionProperties.getDatabase();
        
        // Pool size optimization for bulk operations
        config.setMaximumPoolSize(dbProps.getMaxPoolSize());
        config.setMinimumIdle(Math.max(2, dbProps.getMaxPoolSize() / 4)); // 25% of max pool size, minimum 2
        
        // Connection timeout settings optimized for bulk operations
        config.setConnectionTimeout(dbProps.getConnectionTimeout());
        config.setIdleTimeout(600000); // 10 minutes - longer idle timeout for bulk operations
        config.setMaxLifetime(dbProps.getMaxLifetime());
        config.setValidationTimeout(5000);
        
        // Leak detection for monitoring
        config.setLeakDetectionThreshold(120000); // 2 minutes - longer threshold for bulk operations
        
        // Performance optimizations
        config.setAutoCommit(false); // Manual transaction control for bulk operations
        config.setReadOnly(false);
        config.setIsolateInternalQueries(false);
        config.setAllowPoolSuspension(true);
        config.setRegisterMbeans(true); // Enable JMX monitoring
        
        // Connection pool name for monitoring
        config.setPoolName("HikariCP-BulkOperations");
        
        // Connection test query
        config.setConnectionTestQuery("SELECT 1");
        
        // Additional HikariCP properties for bulk operations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "500"); // Increased for bulk operations
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true"); // Critical for bulk operations
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // PostgreSQL specific optimizations for bulk operations
        config.addDataSourceProperty("defaultRowFetchSize", "1000"); // Optimized for bulk reads
        config.addDataSourceProperty("logUnclosedConnections", "true");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("socketTimeout", "0"); // No socket timeout for long bulk operations
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        // Register metrics with Micrometer
        dataSource.setMetricRegistry(meterRegistry);
        
        logger.info("Initialized optimized HikariCP connection pool for bulk operations: " +
                   "maxPoolSize={}, minIdle={}, connectionTimeout={}ms, maxLifetime={}ms",
                   config.getMaximumPoolSize(), config.getMinimumIdle(), 
                   config.getConnectionTimeout(), config.getMaxLifetime());
        
        return dataSource;
    }
}