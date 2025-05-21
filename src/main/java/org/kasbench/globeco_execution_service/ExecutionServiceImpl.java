package org.kasbench.globeco_execution_service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class ExecutionServiceImpl implements ExecutionService {
    private final ExecutionRepository executionRepository;
    private final KafkaTemplate<String, ExecutionDTO> kafkaTemplate;
    private final String ordersTopic;

    public ExecutionServiceImpl(ExecutionRepository executionRepository, KafkaTemplate<String, ExecutionDTO> kafkaTemplate, @Value("${kafka.topic.orders}") String ordersTopic) {
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
    @Cacheable(value = "execution", key = "#a0")
    public Optional<Execution> findById(Integer id) {
        return executionRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "execution", key = "'all'")
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
                java.time.OffsetDateTime.now(),
                null,
                postDTO.getVersion()
        );
        execution = executionRepository.save(execution);
        // 2. Populate ExecutionDTO, set sentTimestamp = now
        java.time.OffsetDateTime sentTimestamp = java.time.OffsetDateTime.now();
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
                execution.getVersion()
        );
        // 3. Send ExecutionDTO to Kafka
        kafkaTemplate.send(ordersTopic, dto);
        // 4. Update DB with sentTimestamp
        execution.setSentTimestamp(sentTimestamp);
        executionRepository.save(execution);
        return dto;
    }
} 