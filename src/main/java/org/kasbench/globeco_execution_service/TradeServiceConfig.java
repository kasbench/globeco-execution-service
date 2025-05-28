package org.kasbench.globeco_execution_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for trade service HTTP client.
 */
@Configuration
public class TradeServiceConfig {

    @Bean
    public RestTemplate tradeServiceRestTemplate(
            @Value("${trade.service.timeout:5000}") int timeoutMs) {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }
} 