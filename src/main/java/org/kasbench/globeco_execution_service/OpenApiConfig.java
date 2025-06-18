package org.kasbench.globeco_execution_service;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI configuration for the Globeco Execution Service.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI globecoExecutionServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Globeco Execution Service API")
                        .description("RESTful API for managing trade executions with enhanced filtering, pagination, batch processing, and security integration")
                        .version("v1.3.0")
                        .contact(new Contact()
                                .name("GlobeCo Development Team")
                                .email("dev@globeco.com")
                                .url("https://globeco.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8084")
                                .description("Local development server"),
                        new Server()
                                .url("http://globeco-execution-service:8084")
                                .description("Docker container server")))
                .tags(List.of(
                        new Tag()
                                .name("Executions")
                                .description("Operations for managing trade executions"),
                        new Tag()
                                .name("Batch Operations")
                                .description("Bulk operations for creating multiple executions"),
                        new Tag()
                                .name("Health")
                                .description("Health check and monitoring endpoints")));
    }
} 