package org.kasbench.globeco_execution_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ExecutionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutionService executionService;

    @Test
    void testCreateAndGetExecution() throws Exception {
        ExecutionPostDTO postDTO = new ExecutionPostDTO("NEW", "BUY", "NYSE", new BigDecimal("100.00"), new BigDecimal("10.00"), 1);
        String json = objectMapper.writeValueAsString(postDTO);
        // Create
        String response = mockMvc.perform(post("/api/v1/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sentTimestamp").exists())
                .andReturn().getResponse().getContentAsString();
        ExecutionDTO created = objectMapper.readValue(response, ExecutionDTO.class);
        // Get by ID
        mockMvc.perform(get("/api/v1/execution/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()));
    }

    @Test
    void testGetAllExecutions() throws Exception {
        executionService.save(new Execution(null, "NEW", "SELL", "NASDAQ", new BigDecimal("50.00"), null, OffsetDateTime.now(), null, 1));
        mockMvc.perform(get("/api/v1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    // @Test
    // void testUpdateExecution() throws Exception {
    //     Execution execution = executionService.save(new Execution(null, "NEW", "BUY", "NYSE", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), null, 1));
    //     ExecutionPostDTO updateDTO = new ExecutionPostDTO("FILLED", "SELL", "LSE", new BigDecimal("200.00"), new BigDecimal("20.00"), execution.getVersion());
    //     String json = objectMapper.writeValueAsString(updateDTO);
    //     mockMvc.perform(put("/api/v1/blotter/" + execution.getId())
    //             .contentType(MediaType.APPLICATION_JSON)
    //             .content(json))
    //             .andExpect(status().isOk())
    //             .andExpect(jsonPath("$.executionStatus").value("FILLED"));
    // }

    // @Test
    // void testDeleteExecution() throws Exception {
    //     Execution execution = executionService.save(new Execution(null, "NEW", "BUY", "NYSE", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), null, 1));
    //     mockMvc.perform(delete("/api/v1/blotter/" + execution.getId())
    //             .param("version", String.valueOf(execution.getVersion())))
    //             .andExpect(status().isNoContent());
    // }
} 