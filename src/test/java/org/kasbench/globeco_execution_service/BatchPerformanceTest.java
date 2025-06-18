package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for batch operations.
 * These tests validate that batch processing meets performance requirements.
 * Enable with -Dperformance.tests.enabled=true
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@EnabledIfSystemProperty(named = "performance.tests.enabled", matches = "true")
public class BatchPerformanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ExecutionRepository executionRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
        // Clean up before each test
        executionRepository.deleteAll();
    }

    @Test
    void testBatchExecution_SmallBatch_Performance() {
        // Test batch of 10 executions
        int batchSize = 10;
        BatchExecutionRequestDTO batchRequest = createBatchRequest(batchSize);

        Instant start = Instant.now();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BatchExecutionRequestDTO> request = new HttpEntity<>(batchRequest, headers);

        ResponseEntity<BatchExecutionResponseDTO> response = restTemplate.postForEntity(
            baseUrl + "/executions/batch", request, BatchExecutionResponseDTO.class);

        Duration duration = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BatchExecutionResponseDTO batchResponse = response.getBody();
        assertThat(batchResponse).isNotNull();
        assertThat(batchResponse.getStatus()).isEqualTo("SUCCESS");
        assertThat(batchResponse.getSuccessful()).isEqualTo(batchSize);

        // Performance assertion: should complete within 2 seconds for 10 executions
        assertThat(duration.toMillis()).isLessThan(2000);
        
        System.out.println("Batch of " + batchSize + " executions completed in " + duration.toMillis() + "ms");
    }

    @Test
    void testBatchExecution_MediumBatch_Performance() {
        // Test batch of 50 executions
        int batchSize = 50;
        BatchExecutionRequestDTO batchRequest = createBatchRequest(batchSize);

        Instant start = Instant.now();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BatchExecutionRequestDTO> request = new HttpEntity<>(batchRequest, headers);

        ResponseEntity<BatchExecutionResponseDTO> response = restTemplate.postForEntity(
            baseUrl + "/executions/batch", request, BatchExecutionResponseDTO.class);

        Duration duration = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BatchExecutionResponseDTO batchResponse = response.getBody();
        assertThat(batchResponse).isNotNull();
        assertThat(batchResponse.getStatus()).isEqualTo("SUCCESS");
        assertThat(batchResponse.getSuccessful()).isEqualTo(batchSize);

        // Performance assertion: should complete within 5 seconds for 50 executions
        assertThat(duration.toMillis()).isLessThan(5000);
        
        System.out.println("Batch of " + batchSize + " executions completed in " + duration.toMillis() + "ms");
    }

    @Test
    void testBatchExecution_LargeBatch_Performance() {
        // Test batch of 100 executions (maximum allowed)
        int batchSize = 100;
        BatchExecutionRequestDTO batchRequest = createBatchRequest(batchSize);

        Instant start = Instant.now();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BatchExecutionRequestDTO> request = new HttpEntity<>(batchRequest, headers);

        ResponseEntity<BatchExecutionResponseDTO> response = restTemplate.postForEntity(
            baseUrl + "/executions/batch", request, BatchExecutionResponseDTO.class);

        Duration duration = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BatchExecutionResponseDTO batchResponse = response.getBody();
        assertThat(batchResponse).isNotNull();
        assertThat(batchResponse.getStatus()).isEqualTo("SUCCESS");
        assertThat(batchResponse.getSuccessful()).isEqualTo(batchSize);

        // Performance assertion: should complete within 10 seconds for 100 executions
        assertThat(duration.toMillis()).isLessThan(10000);
        
        System.out.println("Batch of " + batchSize + " executions completed in " + duration.toMillis() + "ms");
        
        // Calculate throughput
        double throughput = (double) batchSize / duration.toSeconds();
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " executions/second");
        
        // Performance requirement: at least 10 executions per second
        assertThat(throughput).isGreaterThan(10.0);
    }

    @Test
    void testMultipleConcurrentBatches_Performance() {
        // Test handling multiple small batches concurrently
        int numberOfBatches = 5;
        int batchSize = 20;
        
        List<Thread> threads = new ArrayList<>();
        List<Duration> durations = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();

        Instant overallStart = Instant.now();

        // Create multiple threads to send batches concurrently
        for (int i = 0; i < numberOfBatches; i++) {
            final int batchId = i;
            Thread thread = new Thread(() -> {
                try {
                    BatchExecutionRequestDTO batchRequest = createBatchRequestWithPrefix(batchSize, "BATCH" + batchId + "_");
                    
                    Instant start = Instant.now();
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<BatchExecutionRequestDTO> request = new HttpEntity<>(batchRequest, headers);

                    ResponseEntity<BatchExecutionResponseDTO> response = restTemplate.postForEntity(
                        baseUrl + "/executions/batch", request, BatchExecutionResponseDTO.class);

                    Duration duration = Duration.between(start, Instant.now());
                    
                    synchronized (durations) {
                        durations.add(duration);
                        results.add(response.getStatusCode() == HttpStatus.CREATED);
                    }
                    
                } catch (Exception e) {
                    synchronized (results) {
                        results.add(false);
                    }
                }
            });
            threads.add(thread);
        }

        // Start all threads
        threads.forEach(Thread::start);

        // Wait for all threads to complete
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Duration overallDuration = Duration.between(overallStart, Instant.now());

        // Assertions
        assertThat(results).hasSize(numberOfBatches);
        assertThat(results).allMatch(result -> result); // All should be successful
        
        // Performance assertion: all batches should complete within 15 seconds
        assertThat(overallDuration.toMillis()).isLessThan(15000);
        
        System.out.println("Concurrent processing of " + numberOfBatches + " batches (" + batchSize + " executions each) completed in " + overallDuration.toMillis() + "ms");
        
        // Calculate overall throughput
        int totalExecutions = numberOfBatches * batchSize;
        double overallThroughput = (double) totalExecutions / overallDuration.toSeconds();
        System.out.println("Overall throughput: " + String.format("%.2f", overallThroughput) + " executions/second");
        
        // Performance requirement: at least 5 executions per second under concurrent load
        assertThat(overallThroughput).isGreaterThan(5.0);
    }

    @Test
    void testFilteringPerformance_LargeDataset() {
        // Create a large dataset first
        int datasetSize = 1000;
        createLargeDataset(datasetSize);

        // Test filtering performance on large dataset
        Instant start = Instant.now();
        
        String url = baseUrl + "/executions?executionStatus=NEW&limit=50";
        ResponseEntity<ExecutionPageDTO> response = restTemplate.getForEntity(url, ExecutionPageDTO.class);
        
        Duration duration = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ExecutionPageDTO page = response.getBody();
        assertThat(page).isNotNull();
        
        // Performance assertion: filtering should complete within 1 second even with large dataset
        assertThat(duration.toMillis()).isLessThan(1000);
        
        System.out.println("Filtering " + datasetSize + " records completed in " + duration.toMillis() + "ms");
    }

    // Helper methods

    private BatchExecutionRequestDTO createBatchRequest(int size) {
        return createBatchRequestWithPrefix(size, "SEC");
    }

    private BatchExecutionRequestDTO createBatchRequestWithPrefix(int size, String prefix) {
        List<ExecutionPostDTO> executions = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            ExecutionPostDTO dto = new ExecutionPostDTO();
            dto.setTradeType(i % 2 == 0 ? "BUY" : "SELL");
            dto.setDestination(i % 3 == 0 ? "NYSE" : (i % 3 == 1 ? "NASDAQ" : "LSE"));
            dto.setSecurityId(prefix + String.format("%03d", i + 1));
            dto.setQuantity(new BigDecimal(String.valueOf((i + 1) * 100)));
            dto.setLimitPrice(new BigDecimal(String.format("%.2f", 50.0 + (i * 0.1))));
            executions.add(dto);
        }
        
        BatchExecutionRequestDTO batchRequest = new BatchExecutionRequestDTO();
        batchRequest.setExecutions(executions);
        return batchRequest;
    }

    private void createLargeDataset(int size) {
        for (int i = 0; i < size; i++) {
            Execution execution = new Execution();
            execution.setExecutionStatus(i % 4 == 0 ? "NEW" : (i % 4 == 1 ? "FILLED" : (i % 4 == 2 ? "PARTIALLY_FILLED" : "CANCELLED")));
            execution.setTradeType(i % 2 == 0 ? "BUY" : "SELL");
            execution.setDestination(i % 3 == 0 ? "NYSE" : (i % 3 == 1 ? "NASDAQ" : "LSE"));
            execution.setSecurityId("PERF" + String.format("%04d", i + 1));
            execution.setQuantity(new BigDecimal(String.valueOf((i + 1) * 100)));
            execution.setLimitPrice(new BigDecimal(String.format("%.2f", 50.0 + (i * 0.1))));
            execution.setReceivedTimestamp(java.time.OffsetDateTime.now().minusMinutes(i));
            execution.setVersion(1);
            
            executionRepository.save(execution);
        }
        
        // Flush to ensure all data is persisted
        executionRepository.flush();
    }
} 