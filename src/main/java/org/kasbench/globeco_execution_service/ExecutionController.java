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

    @GetMapping("/executions")
    public List<ExecutionDTO> getAllExecutions() {
        return executionService.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @GetMapping("/execution/{id}")
    public ResponseEntity<ExecutionDTO> getExecutionById(@PathVariable Integer id) {
        Optional<Execution> execution = executionService.findById(id);
        return execution.map(value -> ResponseEntity.ok(toDTO(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/executions")
    public ResponseEntity<ExecutionDTO> createExecution(@RequestBody ExecutionPostDTO postDTO) {
        ExecutionDTO dto = executionService.createAndSendExecution(postDTO);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
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

} 