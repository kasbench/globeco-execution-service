package org.kasbench.globeco_execution_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class HttpMetricsConfiguration {

    @Bean
    public AtomicInteger httpRequestsInFlightCounter() {
        return new AtomicInteger(0);
    }
}