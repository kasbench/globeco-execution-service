package org.kasbench.globeco_execution_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Disabled("Temporarily disabled due to long running.")
class ExecutionControllerTest {
        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private ExecutionService executionService;

        @MockBean
        private TradeServiceClient tradeServiceClient;

        @Test
        @Disabled("Temporarily disabled")
        void testCreateAndGetExecution() throws Exception {
                ExecutionPostDTO postDTO = new ExecutionPostDTO("NEW", "BUY", "NYSE", "SEC123456789012345678901",
                                new BigDecimal("100.00"), new BigDecimal("10.00"), Integer.valueOf(1),
                                Integer.valueOf(1));
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
                executionService.save(new Execution(null, "NEW", "SELL", "NASDAQ", "SEC123456789012345678901",
                                new BigDecimal("50.00"), null, OffsetDateTime.now(), null, 1, BigDecimal.ZERO, null,
                                1));
                mockMvc.perform(get("/api/v1/executions"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content[0].id").exists())
                                .andExpect(jsonPath("$.pagination").exists())
                                .andExpect(jsonPath("$.pagination.totalElements").exists());
        }

        @Test
        @Disabled("Temporarily disabled")
        void testUpdateExecution_PutEndpoint() throws Exception {
                // Mock trade service for this test
                when(tradeServiceClient.getExecutionVersion(any())).thenReturn(Optional.of(1));
                when(tradeServiceClient.updateExecutionFill(any(), any())).thenReturn(true);

                // Create execution
                ExecutionPostDTO postDTO = new ExecutionPostDTO("NEW", "BUY", "NYSE", "SEC123456789012345678901",
                                new BigDecimal("10.00"), new BigDecimal("1.00"), 1, 1);
                String postJson = objectMapper.writeValueAsString(postDTO);
                String response = mockMvc.perform(post("/api/v1/executions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(postJson))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();
                ExecutionDTO created = objectMapper.readValue(response, ExecutionDTO.class);
                // Update: set quantity filled to 4, set avg price
                ExecutionPutDTO putDTO = new ExecutionPutDTO(new BigDecimal("4.00"), new BigDecimal("1.10"),
                                created.getVersion());
                String putJson = objectMapper.writeValueAsString(putDTO);
                String putResponse = mockMvc.perform(put("/api/v1/execution/" + created.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(putJson))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.quantityFilled").value(4.0))
                                .andExpect(jsonPath("$.averagePrice").value(1.10))
                                .andExpect(jsonPath("$.executionStatus").value("PART"))
                                .andReturn().getResponse().getContentAsString();
                ExecutionDTO updated = objectMapper.readValue(putResponse, ExecutionDTO.class);
                // Fetch latest via GET
                String refreshedResponse = mockMvc.perform(get("/api/v1/execution/" + created.getId()))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                ExecutionDTO refreshed = objectMapper.readValue(refreshedResponse, ExecutionDTO.class);
                org.assertj.core.api.Assertions.assertThat(refreshed.getVersion()).isGreaterThan(created.getVersion());
                // Update again: set quantity filled to 10, should become FULL
                ExecutionPutDTO putDTO2 = new ExecutionPutDTO(new BigDecimal("10.00"), new BigDecimal("1.20"),
                                refreshed.getVersion());
                String putJson2 = objectMapper.writeValueAsString(putDTO2);
                String putResponse2 = mockMvc.perform(put("/api/v1/execution/" + created.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(putJson2))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.quantityFilled").value(10.0))
                                .andExpect(jsonPath("$.averagePrice").value(1.20))
                                .andExpect(jsonPath("$.executionStatus").value("FULL"))
                                .andReturn().getResponse().getContentAsString();
                ExecutionDTO updated2 = objectMapper.readValue(putResponse2, ExecutionDTO.class);
                // Fetch latest via GET
                String refreshedResponse2 = mockMvc.perform(get("/api/v1/execution/" + created.getId()))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                ExecutionDTO refreshed2 = objectMapper.readValue(refreshedResponse2, ExecutionDTO.class);
                org.assertj.core.api.Assertions.assertThat(refreshed2.getVersion())
                                .isGreaterThan(refreshed.getVersion());
        }

        // @Test
        // void testDeleteExecution() throws Exception {
        // Execution execution = executionService.save(new Execution(null, "NEW", "BUY",
        // "NYSE", new BigDecimal("100.00"), new BigDecimal("10.00"),
        // OffsetDateTime.now(), null, 1));
        // mockMvc.perform(delete("/api/v1/blotter/" + execution.getId())
        // .param("version", String.valueOf(execution.getVersion())))
        // .andExpect(status().isNoContent());
        // }
}