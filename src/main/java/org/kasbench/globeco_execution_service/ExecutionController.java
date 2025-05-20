package org.kasbench.globeco_execution_service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ExecutionController {
    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/execution")
    public List<ExecutionDTO> getAllExecutions() {
        return executionService.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @GetMapping("/blotter/{id}")
    public ResponseEntity<ExecutionDTO> getExecutionById(@PathVariable Integer id) {
        Optional<Execution> execution = executionService.findById(id);
        return execution.map(value -> ResponseEntity.ok(toDTO(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/blotters")
    public ResponseEntity<ExecutionDTO> createExecution(@RequestBody ExecutionPostDTO postDTO) {
        ExecutionDTO dto = executionService.createAndSendExecution(postDTO);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @PutMapping("/blotter/{id}")
    public ResponseEntity<ExecutionDTO> updateExecution(@PathVariable Integer id, @RequestBody ExecutionPostDTO postDTO) {
        Optional<Execution> existing = executionService.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Execution execution = existing.get();
        execution.setExecutionStatus(postDTO.getExecutionStatus());
        execution.setTradeType(postDTO.getTradeType());
        execution.setDestination(postDTO.getDestination());
        execution.setQuantity(postDTO.getQuantity());
        execution.setLimitPrice(postDTO.getLimitPrice());
        execution.setVersion(postDTO.getVersion());
        Execution updated = executionService.save(execution);
        return ResponseEntity.ok(toDTO(updated));
    }

    @DeleteMapping("/blotter/{id}")
    public ResponseEntity<Void> deleteExecution(@PathVariable Integer id, @RequestParam Integer version) {
        executionService.deleteById(id, version);
        return ResponseEntity.noContent().build();
    }

    private ExecutionDTO toDTO(Execution execution) {
        return new ExecutionDTO(
                execution.getId(),
                execution.getExecutionStatus(),
                execution.getTradeType(),
                execution.getDestination(),
                execution.getQuantity(),
                execution.getLimitPrice(),
                execution.getReceivedTimestamp(),
                execution.getSentTimestamp(),
                execution.getVersion()
        );
    }

    private Execution fromPostDTO(ExecutionPostDTO dto) {
        return new Execution(
                null,
                dto.getExecutionStatus(),
                dto.getTradeType(),
                dto.getDestination(),
                dto.getQuantity(),
                dto.getLimitPrice(),
                null, // receivedTimestamp set in controller
                null, // sentTimestamp
                dto.getVersion()
        );
    }
} 