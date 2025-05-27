package org.kasbench.globeco_execution_service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ExecutionServiceImpl implements ExecutionService {
    private final ExecutionRepository executionRepository;
    private final KafkaTemplate<String, ExecutionDTO> kafkaTemplate;
    private final String ordersTopic;

    public ExecutionServiceImpl(ExecutionRepository executionRepository, KafkaTemplate<String, ExecutionDTO> kafkaTemplate, @Value("${kafka.topic.orders:orders}") String ordersTopic) {
        this.executionRepository = executionRepository;
        this.kafkaTemplate = kafkaTemplate;
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
        // 5. Create DTO with correct version after final save
        ExecutionDTO dto = new ExecutionDTO(
                execution.getId(),
                execution.getExecutionStatus(),
                execution.getTradeType(),
                execution.getDestination(),
                execution.getSecurityId(),
                execution.getQuantity(),
                execution.getLimitPrice(),
                execution.getReceivedTimestamp(),
                sentTimestamp,
                execution.getTradeServiceExecutionId(),
                execution.getQuantityFilled(),
                execution.getAveragePrice(),
                execution.getVersion()
        );
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
        // Increment quantityFilled
        BigDecimal newQuantityFilled = execution.getQuantityFilled() == null ? BigDecimal.ZERO : execution.getQuantityFilled();
        newQuantityFilled = newQuantityFilled.add(putDTO.getQuantityFilled());
        execution.setQuantityFilled(newQuantityFilled);
        // Set averagePrice
        execution.setAveragePrice(putDTO.getAveragePrice());
        // Update executionStatus
        if (newQuantityFilled.compareTo(execution.getQuantity()) < 0) {
            execution.setExecutionStatus("PART");
        } else {
            execution.setExecutionStatus("FULL");
        }
        // Save and return
        execution = executionRepository.save(execution);
        executionRepository.flush();
        return Optional.of(execution);
    }
} 