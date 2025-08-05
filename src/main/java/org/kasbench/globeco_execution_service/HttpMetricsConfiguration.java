package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration class for HTTP metrics collection.
 * Registers Counter, Timer, and Gauge metrics with the MeterRegistry
 * and pre-registers them with sample tags for immediate visibility.
 */
@Configuration
public class HttpMetricsConfiguration {

    private static final String HTTP_REQUESTS_TOTAL = "http_requests_total";
    private static final String HTTP_REQUEST_DURATION = "http_request_duration";
    private static final String HTTP_REQUESTS_IN_FLIGHT = "http_requests_in_flight";

    /**
     * Bean for tracking in-flight HTTP requests using thread-safe atomic operations.
     */
    @Bean
    public AtomicInteger httpRequestsInFlightCounter() {
        return new AtomicInteger(0);
    }

    /**
     * Pre-registers HTTP request counter metric with sample tags for immediate visibility.
     * This ensures the metric appears in monitoring systems even before the first request.
     */
    @Bean
    public Counter httpRequestsCounter(MeterRegistry meterRegistry) {
        // Pre-register with sample tags to ensure immediate visibility
        Counter.builder(HTTP_REQUESTS_TOTAL)
                .description("Total number of HTTP requests")
                .tag("method", "GET")
                .tag("path", "/sample")
                .tag("status", "200")
                .register(meterRegistry);

        return Counter.builder(HTTP_REQUESTS_TOTAL)
                .description("Total number of HTTP requests")
                .register(meterRegistry);
    }

    /**
     * Pre-registers HTTP request duration timer with histogram buckets optimized for millisecond values.
     * Uses buckets: [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000] milliseconds.
     */
    @Bean
    public Timer httpRequestDurationTimer(MeterRegistry meterRegistry) {
        // Pre-register with sample tags to ensure immediate visibility
        Timer.builder(HTTP_REQUEST_DURATION)
                .description("HTTP request duration in milliseconds")
                .tag("method", "GET")
                .tag("path", "/sample")
                .tag("status", "200")
                .register(meterRegistry);

        return Timer.builder(HTTP_REQUEST_DURATION)
                .description("HTTP request duration in milliseconds")
                .register(meterRegistry);
    }

    /**
     * Pre-registers HTTP in-flight requests gauge metric.
     * This gauge tracks the current number of concurrent HTTP requests being processed.
     */
    @Bean
    public Gauge httpRequestsInFlightGauge(MeterRegistry meterRegistry, AtomicInteger httpRequestsInFlightCounter) {
        return Gauge.builder(HTTP_REQUESTS_IN_FLIGHT, httpRequestsInFlightCounter, AtomicInteger::get)
                .description("Current number of HTTP requests being processed")
                .register(meterRegistry);
    }

    /**
     * Registers the HttpMetricsFilter with Spring Boot's filter registration mechanism.
     * Sets high priority (order=1) and URL pattern "/*" to ensure comprehensive coverage
     * of all HTTP traffic including API endpoints and actuator endpoints.
     */
    @Bean
    public FilterRegistrationBean<HttpMetricsFilter> httpMetricsFilterRegistration(HttpMetricsFilter httpMetricsFilter) {
        FilterRegistrationBean<HttpMetricsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(httpMetricsFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // High priority to capture all requests
        registration.setName("httpMetricsFilter");
        return registration;
    }
}