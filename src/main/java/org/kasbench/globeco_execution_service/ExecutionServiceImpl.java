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
    private final BulkExecutionProcessor bulkExecutionProcessor;
    private final AsyncKafkaPublisher asyncKafkaPublisher;
    private final String ordersTopic;

    public ExecutionServiceImpl(
            ExecutionRepository executionRepository, 
            KafkaTemplate<String, ExecutionDTO> kafkaTemplate, 
            TradeServiceClient tradeServiceClient,
            SecurityServiceClient securityServiceClient,
            BulkExecutionProcessor bulkExecutionProcessor,
            AsyncKafkaPublisher asyncKafkaPublisher,
            @Value("${kafka.topic.orders:orders}") String ordersTopic) {
        this.executionRepository = executionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tradeServiceClient = tradeServiceClient;
        this.securityServiceClient = securityServiceClient;
        this.bulkExecutionProcessor = bulkExecutionProcessor;
        this.asyncKafkaPublisher = asyncKafkaPublisher;
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
        // 4. Create DTO with correct version after final save using the conversion method
        ExecutionDTO dto = convertToDTO(execution);
        // 5. Send ExecutionDTO to Kafka asynchronously (non-blocking)
        asyncKafkaPublisher.publishAsync(dto)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to publish execution {} to Kafka: {}", dto.getId(), throwable.getMessage());
                } else if (result.isSuccess()) {
                    logger.debug("Successfully published execution {} to Kafka after {} attempts", 
                        dto.getId(), result.getAttemptCount());
                } else if (!result.isSkipped()) {
                    logger.warn("Failed to publish execution {} to Kafka: {}", dto.getId(), result.getErrorMessage());
                }
            });
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
    public BatchExecutionResponseDTO createBatchExecutions(BatchExecutionRequestDTO batchRequest) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting batch execution processing for {} executions", batchRequest.getExecutions().size());
        
        // Phase 1: Pre-validation using BulkExecutionProcessor
        BulkExecutionProcessor.BatchProcessingContext processingContext = bulkExecutionProcessor.processBatch(batchRequest.getExecutions());
        
        // Create our own BatchProcessingContext for coordination
        BatchProcessingContext context = new BatchProcessingContext(batchRequest.getExecutions());
        
        // Transfer validation results
        transferValidationResults(processingContext, context);
        context.setProcessingPhase(BatchProcessingContext.ProcessingPhase.DATABASE_OPERATIONS);
        
        long validationTime = System.currentTimeMillis();
        logger.info("Pre-validation completed in {}ms: {} valid, {} invalid executions", 
            validationTime - startTime, context.getValidExecutionsOnly().size(), context.getValidationErrorIndices().size());
        
        // Phase 2: Bulk database operations with optimized transaction boundaries
        if (!context.getValidExecutionsOnly().isEmpty()) {
            processBulkDatabaseOperations(context);
        }
        
        long databaseTime = System.currentTimeMillis();
        logger.info("Database operations completed in {}ms: {} successful, {} failed", 
            databaseTime - validationTime, context.getSuccessfulDatabaseIndices().size(), context.getDatabaseErrorIndices().size());
        
        // Phase 3: Update sent timestamps for successful executions
        updateSentTimestampsForSuccessfulExecutions(context);
        
        long timestampTime = System.currentTimeMillis();
        logger.info("Timestamp updates completed in {}ms", timestampTime - databaseTime);
        
        // Phase 4: Asynchronous Kafka publishing for successful executions
        context.setProcessingPhase(BatchProcessingContext.ProcessingPhase.KAFKA_PUBLISHING);
        publishSuccessfulExecutionsToKafka(context);
        
        long kafkaTime = System.currentTimeMillis();
        logger.info("Kafka publishing initiated in {}ms", kafkaTime - timestampTime);
        
        // Mark processing as complete and return response
        context.setProcessingPhase(BatchProcessingContext.ProcessingPhase.COMPLETED);
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Batch execution processing completed in {}ms total", totalTime);
        
        return context.getBatchResponse();
    }
    
    /**
     * Process bulk database operations with optimized transaction boundaries.
     * Uses separate transactions for each batch to avoid long-running transactions.
     */
    private void processBulkDatabaseOperations(BatchProcessingContext context) {
        List<Execution> validExecutions = context.getValidExecutionsOnly();
        
        try {
            // Use bulk insert for optimal performance
            List<Execution> savedExecutions = bulkInsertExecutions(validExecutions);
            
            // Update context with successful database operations
            for (int i = 0; i < savedExecutions.size(); i++) {
                Execution savedExecution = savedExecutions.get(i);
                // Find the original request index for this execution
                int originalIndex = findOriginalRequestIndex(context, savedExecution, i);
                context.recordDatabaseSuccess(originalIndex, savedExecution);
            }
            
        } catch (Exception e) {
            logger.error("Bulk database operation failed, falling back to individual inserts: {}", e.getMessage());
            // Fallback to individual inserts for error isolation
            processFallbackIndividualInserts(context, validExecutions);
        }
    }
    
    /**
     * Perform bulk insert operations using the repository's bulk insert method.
     */
    @Transactional
    private List<Execution> bulkInsertExecutions(List<Execution> executions) {
        logger.debug("Performing bulk insert for {} executions", executions.size());
        return executionRepository.bulkInsert(executions);
    }
    
    /**
     * Fallback method to process individual inserts when bulk operations fail.
     */
    private void processFallbackIndividualInserts(BatchProcessingContext context, List<Execution> validExecutions) {
        logger.info("Processing {} executions individually as fallback", validExecutions.size());
        
        for (int i = 0; i < validExecutions.size(); i++) {
            Execution execution = validExecutions.get(i);
            int originalIndex = findOriginalRequestIndex(context, execution, i);
            
            try {
                Execution savedExecution = saveIndividualExecution(execution);
                context.recordDatabaseSuccess(originalIndex, savedExecution);
                logger.debug("Successfully saved execution at index {} individually", originalIndex);
                
            } catch (Exception e) {
                logger.error("Failed to save execution at index {} individually: {}", originalIndex, e.getMessage());
                context.recordDatabaseError(originalIndex, e);
            }
        }
    }
    
    /**
     * Save a single execution in its own transaction.
     */
    @Transactional
    private Execution saveIndividualExecution(Execution execution) {
        return executionRepository.saveAndFlush(execution);
    }
    
    /**
     * Update sent timestamps for all successfully saved executions.
     */
    private void updateSentTimestampsForSuccessfulExecutions(BatchProcessingContext context) {
        Set<Integer> successfulIndices = context.getSuccessfulDatabaseIndices();
        if (successfulIndices.isEmpty()) {
            logger.debug("No successful executions to update timestamps for");
            return;
        }
        
        // Collect execution IDs from successful operations
        List<Integer> executionIds = new ArrayList<>();
        for (ExecutionResultDTO result : context.getResults()) {
            if (result != null && "SUCCESS".equals(result.getStatus()) && result.getExecution() != null) {
                executionIds.add(result.getExecution().getId());
            }
        }
        
        if (!executionIds.isEmpty()) {
            try {
                OffsetDateTime sentTimestamp = OffsetDateTime.now();
                bulkUpdateSentTimestamps(executionIds, sentTimestamp);
                logger.debug("Updated sent timestamps for {} executions", executionIds.size());
                
                // Update the DTOs in the context with the sent timestamp
                updateContextWithSentTimestamps(context, sentTimestamp);
                
            } catch (Exception e) {
                logger.error("Failed to bulk update sent timestamps: {}", e.getMessage());
                // This is not critical for the success of the batch operation
            }
        }
    }
    
    /**
     * Bulk update sent timestamps using the repository's bulk update method.
     */
    @Transactional
    private void bulkUpdateSentTimestamps(List<Integer> executionIds, OffsetDateTime sentTimestamp) {
        executionRepository.bulkUpdateSentTimestamp(executionIds, sentTimestamp);
    }
    
    /**
     * Update the context results with the sent timestamps.
     */
    private void updateContextWithSentTimestamps(BatchProcessingContext context, OffsetDateTime sentTimestamp) {
        for (int i = 0; i < context.getResults().size(); i++) {
            ExecutionResultDTO result = context.getResults().get(i);
            if (result != null && "SUCCESS".equals(result.getStatus()) && result.getExecution() != null) {
                // Create updated DTO with sent timestamp
                ExecutionDTO updatedDTO = createUpdatedDTOWithSentTimestamp(result.getExecution(), sentTimestamp);
                ExecutionResultDTO updatedResult = ExecutionResultDTO.success(i, updatedDTO);
                context.getResults().set(i, updatedResult);
            }
        }
    }
    
    /**
     * Create an updated ExecutionDTO with the sent timestamp.
     */
    private ExecutionDTO createUpdatedDTOWithSentTimestamp(ExecutionDTO originalDTO, OffsetDateTime sentTimestamp) {
        return new ExecutionDTO(
            originalDTO.getId(),
            originalDTO.getExecutionStatus(),
            originalDTO.getTradeType(),
            originalDTO.getDestination(),
            originalDTO.getSecurity(),
            originalDTO.getQuantity(),
            originalDTO.getLimitPrice(),
            originalDTO.getReceivedTimestamp(),
            sentTimestamp, // Updated sent timestamp
            originalDTO.getTradeServiceExecutionId(),
            originalDTO.getQuantityFilled(),
            originalDTO.getAveragePrice(),
            originalDTO.getVersion()
        );
    }
    
    /**
     * Find the original request index for a given execution.
     * This is needed to map back from the validated executions list to the original request indices.
     */
    private int findOriginalRequestIndex(BatchProcessingContext context, Execution execution, int validExecutionIndex) {
        // Since we process executions in order and only include valid ones,
        // we need to map back to the original request index
        List<ExecutionPostDTO> originalRequests = context.getOriginalRequests();
        Set<Integer> validationErrorIndices = context.getValidationErrorIndices();
        
        int validExecutionCount = 0;
        for (int i = 0; i < originalRequests.size(); i++) {
            if (!validationErrorIndices.contains(i)) {
                if (validExecutionCount == validExecutionIndex) {
                    return i;
                }
                validExecutionCount++;
            }
        }
        
        // Fallback - should not happen in normal operation
        logger.warn("Could not find original request index for valid execution at index {}", validExecutionIndex);
        return validExecutionIndex;
    }
    
    /**
     * Transfer validation results from BulkExecutionProcessor context to our BatchProcessingContext.
     */
    private void transferValidationResults(BulkExecutionProcessor.BatchProcessingContext processingContext, 
                                         BatchProcessingContext context) {
        // Transfer validation errors
        for (Map.Entry<Integer, BulkExecutionProcessor.ValidationException> entry : processingContext.getValidationErrors().entrySet()) {
            context.addValidationError(entry.getKey(), entry.getValue());
        }
        
        // Transfer validated executions
        List<Execution> validExecutions = processingContext.getValidatedExecutions();
        for (int i = 0; i < validExecutions.size(); i++) {
            Execution execution = validExecutions.get(i);
            if (execution != null) {
                // Find the original request index for this execution
                int originalIndex = findOriginalRequestIndexForValidation(processingContext, i);
                context.addValidatedExecution(originalIndex, execution);
            }
        }
    }
    
    /**
     * Find the original request index for a validated execution.
     */
    private int findOriginalRequestIndexForValidation(BulkExecutionProcessor.BatchProcessingContext processingContext, 
                                                    int validExecutionIndex) {
        // Since BulkExecutionProcessor processes executions in order and only includes valid ones,
        // we need to map back to the original request index
        Set<Integer> validationErrorIndices = processingContext.getValidationErrors().keySet();
        
        int validExecutionCount = 0;
        for (int i = 0; i < processingContext.getOriginalRequests().size(); i++) {
            if (!validationErrorIndices.contains(i)) {
                if (validExecutionCount == validExecutionIndex) {
                    return i;
                }
                validExecutionCount++;
            }
        }
        
        // Fallback - should not happen in normal operation
        logger.warn("Could not find original request index for valid execution at index {}", validExecutionIndex);
        return validExecutionIndex;
    }
    
    /**
     * Publish successful executions to Kafka asynchronously.
     * Kafka failures don't affect the overall success status since database persistence is primary.
     */
    private void publishSuccessfulExecutionsToKafka(BatchProcessingContext context) {
        // Collect successful execution DTOs for Kafka publishing
        List<ExecutionDTO> successfulExecutions = new ArrayList<>();
        List<Integer> successfulIndices = new ArrayList<>();
        
        for (int i = 0; i < context.getResults().size(); i++) {
            ExecutionResultDTO result = context.getResults().get(i);
            if (result != null && "SUCCESS".equals(result.getStatus()) && result.getExecution() != null) {
                successfulExecutions.add(result.getExecution());
                successfulIndices.add(i);
            }
        }
        
        if (successfulExecutions.isEmpty()) {
            logger.debug("No successful executions to publish to Kafka");
            return;
        }
        
        logger.info("Publishing {} successful executions to Kafka asynchronously", successfulExecutions.size());
        
        // Publish batch asynchronously and handle results
        asyncKafkaPublisher.publishBatchAsync(successfulExecutions)
            .whenComplete((batchResult, throwable) -> {
                if (throwable != null) {
                    logger.error("Unexpected error during batch Kafka publishing: {}", throwable.getMessage(), throwable);
                    // Record Kafka errors for all executions
                    for (int i = 0; i < successfulIndices.size(); i++) {
                        context.recordKafkaError(successfulIndices.get(i), new RuntimeException(throwable));
                    }
                } else {
                    // Process individual results
                    handleKafkaPublishResults(context, successfulIndices, batchResult);
                }
            });
    }
    
    /**
     * Handle the results of Kafka publishing and update the context accordingly.
     */
    private void handleKafkaPublishResults(BatchProcessingContext context, List<Integer> successfulIndices, 
                                         AsyncKafkaPublisher.BatchPublishResult batchResult) {
        
        logger.info("Kafka batch publishing completed: {} successful, {} failed, {} skipped out of {} total",
            batchResult.getSuccessfulMessages(), batchResult.getFailedMessages(), 
            batchResult.getSkippedMessages(), batchResult.getTotalMessages());
        
        // Update context with individual Kafka results
        List<AsyncKafkaPublisher.PublishResult> results = batchResult.getResults();
        for (int i = 0; i < results.size() && i < successfulIndices.size(); i++) {
            AsyncKafkaPublisher.PublishResult publishResult = results.get(i);
            int originalIndex = successfulIndices.get(i);
            
            if (publishResult.isSuccess()) {
                context.recordKafkaSuccess(originalIndex);
                logger.debug("Kafka publishing successful for execution at index {} (attempts: {})", 
                    originalIndex, publishResult.getAttemptCount());
            } else if (publishResult.isSkipped()) {
                logger.debug("Kafka publishing skipped for execution at index {} (async Kafka disabled)", originalIndex);
                // Skipped messages are not considered errors
            } else {
                context.recordKafkaError(originalIndex, new RuntimeException(publishResult.getErrorMessage()));
                logger.warn("Kafka publishing failed for execution at index {} after {} attempts: {}", 
                    originalIndex, publishResult.getAttemptCount(), publishResult.getErrorMessage());
            }
        }
        
        // Log Kafka publishing metrics
        logKafkaPublishingMetrics();
    }
    
    /**
     * Log current Kafka publishing metrics for monitoring.
     */
    private void logKafkaPublishingMetrics() {
        try {
            AsyncKafkaPublisher.PublishMetrics metrics = asyncKafkaPublisher.getMetrics();
            logger.info("Kafka publishing metrics - Total attempts: {}, Success rate: {:.2f}%, " +
                       "Failed: {}, Retried: {}, DLQ: {}, Circuit breaker: {}", 
                metrics.getTotalAttempts(), metrics.getSuccessRate() * 100,
                metrics.getFailedPublishes(), metrics.getRetriedPublishes(), 
                metrics.getDeadLetterMessages(), metrics.getCircuitState());
        } catch (Exception e) {
            logger.debug("Failed to retrieve Kafka publishing metrics: {}", e.getMessage());
        }
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