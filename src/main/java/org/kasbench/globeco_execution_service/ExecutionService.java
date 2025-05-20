package org.kasbench.globeco_execution_service;

import java.util.List;
import java.util.Optional;

public interface ExecutionService {
    Execution save(Execution execution);
    Optional<Execution> findById(Integer id);
    List<Execution> findAll();
    void deleteById(Integer id, Integer version);
} 