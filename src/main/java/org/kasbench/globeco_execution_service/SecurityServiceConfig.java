package org.kasbench.globeco_execution_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for Security Service integration.
 */
@Configuration
public class SecurityServiceConfig {
    
    @Bean("securityServiceRestTemplate")
    public RestTemplate securityServiceRestTemplate(
            @Value("${security-service.timeout.connect:5s}") Duration connectTimeout,
            @Value("${security-service.timeout.read:10s}") Duration readTimeout) {
        
        return new RestTemplateBuilder()
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
} 