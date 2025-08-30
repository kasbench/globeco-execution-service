package org.kasbench.globeco_execution_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExecutionServiceImpl implements ExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionServiceImpl.class);
    
    private final ExecutionRepository executionRepository;
    private final KafkaTemplate<String, ExecutionDTO> kafkaTemplate;
    private final TradeServiceClient tradeServiceClient;
    private final SecurityServiceClient securityServiceClient;
    private final String ordersTopic;

    public ExecutionServiceImpl(
            ExecutionRepository executionRepository, 
            KafkaTemplate<String, ExecutionDTO> kafkaTemplate, 
            TradeServiceClient tradeServiceClient,
            SecurityServiceClient securityServiceClient,
            @Value("${kafka.topic.orders:orders}") String ordersTopic) {
        this.executionRepository = executionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tradeServiceClient = tradeServiceClient;
        this.securityServiceClient = securityServiceClient;
        this.ordersTopic = ordersTopic;
    }

    @Override
    @Transactional
    public Execution save(Execution execution) {
        return executionRepository.save(execution);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Execution> findById(Integer id) {
        return executionRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Execution> findAll() {
        return executionRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public ExecutionPageDTO findExecutions(ExecutionQueryParams queryParams) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting findExecutions query - offset: {}, limit: {}, filters: [id: {}, status: {}, tradeType: {}, destination: {}, ticker: {}]", 
            queryParams.getId(), queryParams.getOffset(), queryParams.getLimit(), 
            queryParams.getExecutionStatus(), queryParams.getTradeType(), 
            queryParams.getDestination(), queryParams.getTicker());
        
        // Handle single ID lookup first
        if (queryParams.getId() != null) {
            logger.debug("Using direct ID lookup for execution: {}", queryParams.getId());
            Optional<Execution> execution = executionRepository.findById(queryParams.getId());
            
            if (execution.isEmpty()) {
                // Return empty page
                PaginationDTO emptyPagination = new PaginationDTO(0, 1, 0L, 0, 0, false, false);
                return new ExecutionPageDTO(List.of(), emptyPagination);
            }
            
            // Convert to DTO with security information
            List<ExecutionDTO> executionDTOs = convertToDTOsBatch(List.of(execution.get()));
            
            PaginationDTO pagination = new PaginationDTO(0, 1, 1L, 1, 0, false, false);
            return new ExecutionPageDTO(executionDTOs, pagination);
        }
        
        // Parse sort parameters
        Sort sort = SortUtils.parseSortBy(queryParams.getSortBy());
        long sortParseTime = System.currentTimeMillis();
        logger.debug("Sort parsing completed in {}ms", sortParseTime - startTime);
        
        // Create pageable
        Pageable pageable = PageRequest.of(
            queryParams.getOffset() / queryParams.getLimit(), 
            queryParams.getLimit(), 
            sort
        );
        
        // Check if we can use the optimized query path (no ticker filter, simple sorting)
        boolean canUseOptimizedPath = (queryParams.getTicker() == null || queryParams.getTicker().trim().isEmpty()) &&
                                     "id".equals(queryParams.getSortBy());
        
        Page<Execution> page;
        long queryStartTime = System.currentTimeMillis();
        
        if (canUseOptimizedPath) {
            logger.debug("Using optimized query path");
            
            // Resolve security ID if ticker is provided (but we already checked it's null above)
            String securityId = null;
            
            // Use optimized native query
            List<Execution> executions = executionRepository.findExecutionsOptimized(
                queryParams.getExecutionStatus(),
                queryParams.getTradeType(), 
                queryParams.getDestination(),
                securityId,
                queryParams.getOffset(),
                queryParams.getLimit()
            );
            
            // Get total count
            long totalElements = executionRepository.countExecutionsOptimized(
                queryParams.getExecutionStatus(),
                queryParams.getTradeType(),
                queryParams.getDestination(), 
                securityId
            );
            
            // Create a Page manually
            int pageNumber = queryParams.getOffset() / queryParams.getLimit();
            int totalPages = (int) Math.ceil((double) totalElements / queryParams.getLimit());
            boolean hasNext = (pageNumber + 1) < totalPages;
            boolean hasPrevious = pageNumber > 0;
            
            page = new org.springframework.data.domain.PageImpl<>(
                executions, pageable, totalElements
            );
            
        } else {
            logger.debug("Using JPA Specification query path");
            
            // Create specification for filtering - pass SecurityServiceClient for ticker resolution
            long specStartTime = System.currentTimeMillis();
            Specification<Execution> spec = ExecutionSpecification.withQueryParams(queryParams, securityServiceClient);
            long specEndTime = System.currentTimeMillis();
            logger.debug("Specification creation completed in {}ms", specEndTime - specStartTime);
            
            // Execute query
            page = executionRepository.findAll(spec, pageable);
        }
        
        long queryEndTime = System.currentTimeMillis();
        logger.info("Database query completed in {}ms - returned {} executions out of {} total (optimized: {})", 
            queryEndTime - queryStartTime, page.getContent().size(), page.getTotalElements(), canUseOptimizedPath);
        
        // Convert to DTOs with security information using batch optimization
        long dtoStartTime = System.currentTimeMillis();
        List<ExecutionDTO> executionDTOs = convertToDTOsBatch(page.getContent());
        long dtoEndTime = System.currentTimeMillis();
        logger.info("DTO conversion with security enrichment completed in {}ms", dtoEndTime - dtoStartTime);
        
        // Create pagination metadata
        PaginationDTO pagination = new PaginationDTO(
            queryParams.getOffset(),
            queryParams.getLimit(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.hasNext(),
            page.hasPrevious()
        );
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Total findExecutions operation completed in {}ms - breakdown: query={}ms, dto_conversion={}ms", 
            totalTime, queryEndTime - queryStartTime, dtoEndTime - dtoStartTime);
        
        return new ExecutionPageDTO(executionDTOs, pagination);
    }
    
    /**
     * Convert Execution entity to ExecutionDTO with security information.
     */
    private ExecutionDTO convertToDTO(Execution execution) {
        // Get security information
        SecurityDTO security = null;
        if (execution.getSecurityId() != null) {
            try {
                Optional<SecurityDTO> securityOpt = securityServiceClient.getSecurityById(execution.getSecurityId());
                if (securityOpt.isPresent()) {
                    security = securityOpt.get();
                } else {
                    // Create a security DTO with just the ID if not found
                    security = new SecurityDTO(execution.getSecurityId(), null);
                }
            } catch (Exception e) {
                logger.warn("Failed to get security info for securityId {}: {}", 
                    execution.getSecurityId(), e.getMessage());
                // Create a security DTO with just the ID if the service call fails
                security = new SecurityDTO(execution.getSecurityId(), null);
            }
        }
        
        return new ExecutionDTO(
            execution.getId(),
            execution.getExecutionStatus(),
            execution.getTradeType(),
            execution.getDestination(),
            security,
            execution.getQuantity(),
            execution.getLimitPrice(),
            execution.getReceivedTimestamp(),
            execution.getSentTimestamp(),
            execution.getTradeServiceExecutionId(),
            execution.getQuantityFilled(),
            execution.getAveragePrice(),
            execution.getVersion()
        );
    }
    
    /**
     * Optimized batch conversion to avoid N+1 queries to Security Service.
     * Collects unique security IDs and makes batch calls instead of individual calls.
     */
    private List<ExecutionDTO> convertToDTOsBatch(List<Execution> executions) {
        if (executions.isEmpty()) {
            return new ArrayList<>();
        }
        
        long startTime = System.currentTimeMillis();
        
        // Collect unique security IDs
        Set<String> uniqueSecurityIds = executions.stream()
            .map(Execution::getSecurityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        logger.debug("Found {} unique security IDs from {} executions", uniqueSecurityIds.size(), executions.size());
        
        // Batch fetch security information using optimized batch method
        final Map<String, SecurityDTO> securityMap = fetchSecurityInformation(uniqueSecurityIds);
        
        // Convert executions to DTOs using the pre-fetched security data
        List<ExecutionDTO> executionDTOs = executions.stream()
            .map(execution -> {
                SecurityDTO security = null;
                if (execution.getSecurityId() != null) {
                    security = securityMap.get(execution.getSecurityId());
                }
                
                return new ExecutionDTO(
                    execution.getId(),
                    execution.getExecutionStatus(),
                    execution.getTradeType(),
                    execution.getDestination(),
                    security,
                    execution.getQuantity(),
                    execution.getLimitPrice(),
                    execution.getReceivedTimestamp(),
                    execution.getSentTimestamp(),
                    execution.getTradeServiceExecutionId(),
                    execution.getQuantityFilled(),
                    execution.getAveragePrice(),
                    execution.getVersion()
                );
            })
            .collect(Collectors.toList());
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.debug("Batch DTO conversion completed in {}ms", totalTime);
        
        return executionDTOs;
    }
    
    /**
     * Helper method to fetch security information for a set of security IDs.
     */
    private Map<String, SecurityDTO> fetchSecurityInformation(Set<String> uniqueSecurityIds) {
        if (uniqueSecurityIds.isEmpty()) {
            return new HashMap<>();
        }
        
        long securityFetchStart = System.currentTimeMillis();
        
        try {
            Map<String, SecurityDTO> result = securityServiceClient.getSecuritiesByIds(uniqueSecurityIds);
            long securityFetchEnd = System.currentTimeMillis();
            logger.info("Security service batch fetch completed in {}ms for {} unique securities", 
                securityFetchEnd - securityFetchStart, uniqueSecurityIds.size());
            return result;
            
        } catch (Exception e) {
            logger.error("Batch security fetch failed, falling back to individual calls: {}", e.getMessage());
            
            // Fallback to individual calls if batch fails
            Map<String, SecurityDTO> fallbackMap = new HashMap<>();
            for (String securityId : uniqueSecurityIds) {
                try {
                    Optional<SecurityDTO> securityOpt = securityServiceClient.getSecurityById(securityId);
                    if (securityOpt.isPresent()) {
                        fallbackMap.put(securityId, securityOpt.get());
                    } else {
                        fallbackMap.put(securityId, new SecurityDTO(securityId, null));
                    }
                } catch (Exception individualError) {
                    logger.warn("Failed to get security info for securityId {}: {}", securityId, individualError.getMessage());
                    fallbackMap.put(securityId, new SecurityDTO(securityId, null));
                }
            }
            
            long securityFetchEnd = System.currentTimeMillis();
            logger.info("Security service fallback fetch completed in {}ms for {} unique securities", 
                securityFetchEnd - securityFetchStart, uniqueSecurityIds.size());
            
            return fallbackMap;
        }
    }

    @Override
    @Transactional
    public void deleteById(Integer id, Integer version) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found with id: " + id));
        if (!execution.getVersion().equals(version)) {
            throw new OptimisticLockingFailureException("Version mismatch for execution with id: " + id);
        }
        executionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public ExecutionDTO createAndSendExecution(ExecutionPostDTO postDTO) {
        // 1. Save API data to execution table (receivedTimestamp = now, sentTimestamp = null)
        Execution execution = new Execution(
                null,
                postDTO.getExecutionStatus(),
                postDTO.getTradeType(),
                postDTO.getDestination(),
                postDTO.getSecurityId(),
                postDTO.getQuantity(),
                postDTO.getLimitPrice(),
                OffsetDateTime.now(),
                null,
                postDTO.getTradeServiceExecutionId(),
                BigDecimal.ZERO,
                null,
                null
        );
        execution = executionRepository.saveAndFlush(execution);
        // 2. Populate ExecutionDTO, set sentTimestamp = now
        OffsetDateTime sentTimestamp = OffsetDateTime.now();
        // 3. Update DB with sentTimestamp
        execution.setSentTimestamp(sentTimestamp);
        execution = executionRepository.saveAndFlush(execution);
        // 5. Create DTO with correct version after final save using the conversion method
        ExecutionDTO dto = convertToDTO(execution);
        // 5. Send ExecutionDTO to Kafka
        kafkaTemplate.send(ordersTopic, dto);
        return dto;
    }

    @Override
    @Transactional
    public Optional<Execution> updateExecution(Integer id, ExecutionPutDTO putDTO) {
        Optional<Execution> optionalExecution = executionRepository.findById(id);
        if (optionalExecution.isEmpty()) {
            return Optional.empty();
        }
        Execution execution = optionalExecution.get();
        // Optimistic concurrency check
        if (!execution.getVersion().equals(putDTO.getVersion())) {
            throw new OptimisticLockingFailureException("Version mismatch for execution with id: " + id);
        }
        // Set quantityFilled (total quantity, not delta)
        execution.setQuantityFilled(putDTO.getQuantityFilled());
        // Set averagePrice
        execution.setAveragePrice(putDTO.getAveragePrice());
        // Update executionStatus
        if (execution.getQuantityFilled().compareTo(execution.getQuantity()) < 0) {
            execution.setExecutionStatus("PART");
        } else {
            execution.setExecutionStatus("FULL");
        }
        // Save and return
        execution = executionRepository.save(execution);
        executionRepository.flush();
        
        // Update trade service asynchronously (non-blocking)
        updateTradeServiceAsync(execution);
        
        return Optional.of(execution);
    }
    
    @Override
    @Transactional
    public BatchExecutionResponseDTO createBatchExecutions(BatchExecutionRequestDTO batchRequest) {
        List<ExecutionResultDTO> results = new ArrayList<>();
        
        // Process each execution in the batch
        for (int i = 0; i < batchRequest.getExecutions().size(); i++) {
            ExecutionPostDTO postDTO = batchRequest.getExecutions().get(i);
            
            try {
                // Create individual execution using existing method
                ExecutionDTO executionDTO = createAndSendExecution(postDTO);
                results.add(ExecutionResultDTO.success(i, executionDTO));
                
                logger.debug("Successfully created execution {} (index {}) in batch", 
                    executionDTO.getId(), i);
                    
            } catch (Exception e) {
                // Log the error and continue with other executions
                logger.error("Failed to create execution at index {} in batch: {}", i, e.getMessage(), e);
                results.add(ExecutionResultDTO.failure(i, e.getMessage()));
            }
        }
        
        // Create and return the batch response
        BatchExecutionResponseDTO response = BatchExecutionResponseDTO.fromResults(results);
        
        logger.info("Batch execution completed: {} successful, {} failed out of {} total", 
            response.getSuccessful(), response.getFailed(), response.getTotalRequested());
            
        return response;
    }
    
    /**
     * Update the trade service with execution fill information.
     * This method is called asynchronously to avoid blocking the main transaction.
     */
    private void updateTradeServiceAsync(Execution execution) {
        // Only update if we have a trade service execution ID
        if (execution.getTradeServiceExecutionId() == null) {
            logger.debug("No trade service execution ID for execution {}, skipping trade service update", execution.getId());
            return;
        }
        
        try {
            logger.debug("Updating trade service for execution {} with trade service ID {}", 
                    execution.getId(), execution.getTradeServiceExecutionId());
            
            // Get current version from trade service
            Optional<Integer> tradeServiceVersion = tradeServiceClient.getExecutionVersion(execution.getTradeServiceExecutionId());
            if (tradeServiceVersion.isEmpty()) {
                logger.warn("Could not retrieve version from trade service for execution {}", execution.getTradeServiceExecutionId());
                return;
            }
            
            // Create fill DTO
            TradeServiceExecutionFillDTO fillDTO = new TradeServiceExecutionFillDTO(
                    execution.getExecutionStatus(),
                    execution.getQuantityFilled(),
                    tradeServiceVersion.get()
            );
            
            // Update trade service
            boolean success = tradeServiceClient.updateExecutionFill(execution.getTradeServiceExecutionId(), fillDTO);
            if (success) {
                logger.debug("Successfully updated trade service for execution {}", execution.getId());
            } else {
                logger.warn("Failed to update trade service for execution {}", execution.getId());
            }
        } catch (Exception e) {
            logger.error("Error updating trade service for execution {}: {}", execution.getId(), e.getMessage(), e);
        }
    }
} 