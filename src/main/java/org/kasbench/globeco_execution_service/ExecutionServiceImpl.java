package org.kasbench.globeco_execution_service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.Optional;

@Service
public class ExecutionServiceImpl implements ExecutionService {
    private final ExecutionRepository executionRepository;

    public ExecutionServiceImpl(ExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Override
    @Transactional
    public Execution save(Execution execution) {
        return executionRepository.save(execution);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "execution", key = "#id")
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
} 