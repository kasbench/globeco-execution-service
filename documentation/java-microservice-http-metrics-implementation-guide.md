# Java Microservice HTTP Metrics Implementation Guide

## Overview

This guide provides a battle-tested approach for implementing custom HTTP metrics in Java microservices using Spring Boot, Micrometer, and OpenTelemetry. It addresses common pitfalls and unit conversion issues encountered when exporting metrics to both Prometheus (direct scraping) and OpenTelemetry Collector.

## Key Findings & Hard-Won Lessons

### Unit Conversion Reality
- **Direct Prometheus Scraping**: Expects duration in seconds, displays fractional seconds (0.005, 0.01, 0.025)
- **OpenTelemetry Collector**: Interprets duration differently, displays millisecond values as whole numbers (5.0, 10.0, 25.0)
- **Recommendation**: Use millisecond-based durations for consistency across both export methods

### Critical Implementation Notes
- MeterFilter approaches can cause timing conflicts and should be avoided for histogram configuration
- Explicit Timer configuration in the filter provides the most reliable results
- Jakarta EE (not Java EE) servlet API required for Spring Boot 3+
- Thread-local cleanup is essential for proper resource management
- Recording in milliseconds works better than nanoseconds for cross-platform consistency

## Implementation

### 1. Dependencies (build.gradle)

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-core'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    // For OpenTelemetry export
    implementation 'io.micrometer:micrometer-registry-otlp'
}
```

### 2. Application Properties

```properties
# Enable Actuator endpoints
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true

# OpenTelemetry configuration (if using OTLP export)
management.otlp.metrics.export.url=http://otel-collector:4318/v1/metrics
management.otlp.metrics.export.step=1m
management.otlp.metrics.export.resource-attributes.service.name=your-service-name
management.otlp.metrics.export.resource-attributes.service.version=1.0.0
```

### 3. HTTP Metrics Configuration

```java
package com.example.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class HttpMetricsConfiguration {

    private final AtomicInteger inFlightRequests = new AtomicInteger(0);

    @Bean
    public Counter httpRequestsTotal(MeterRegistry registry) {
        return Counter.builder("http_requests_total")
                .description("Total number of HTTP requests")
                .register(registry);
    }

    @Bean
    public Gauge httpRequestsInFlight(MeterRegistry registry) {
        return Gauge.builder("http_requests_in_flight", inFlightRequests, AtomicInteger::get)
                .description("Number of HTTP requests currently being processed")
                .register(registry);
    }

    @Bean
    public AtomicInteger inFlightRequestsCounter() {
        return inFlightRequests;
    }

    @Bean
    public FilterRegistrationBean<HttpMetricsFilter> httpMetricsFilterRegistration(HttpMetricsFilter httpMetricsFilter) {
        FilterRegistrationBean<HttpMetricsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(httpMetricsFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // High priority to capture all requests
        registration.setName("httpMetricsFilter");
        return registration;
    }

    @Bean
    public String httpMetricsInitializer(MeterRegistry registry) {
        // Pre-register counter with sample tags for metrics discovery
        Counter.builder("http_requests_total")
                .description("Total number of HTTP requests")
                .tag("method", "GET")
                .tag("path", "/health")
                .tag("status", "200")
                .register(registry);
                
        return "HTTP metrics pre-registered successfully";
    }
}
```

### 4. HTTP Metrics Filter (The Core Implementation)

```java
package com.example.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HttpMetricsFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(HttpMetricsFilter.class);

    private final MeterRegistry meterRegistry;
    private final AtomicInteger inFlightRequestsCounter;

    private static final ThreadLocal<RequestMetrics> requestMetricsHolder = new ThreadLocal<>();

    @Autowired
    public HttpMetricsFilter(MeterRegistry meterRegistry, 
                           AtomicInteger inFlightRequestsCounter) {
        this.meterRegistry = meterRegistry;
        this.inFlightRequestsCounter = inFlightRequestsCounter;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Start timing and increment in-flight counter
        long startTime = System.nanoTime();
        requestMetricsHolder.set(new RequestMetrics(startTime, true));
        inFlightRequestsCounter.incrementAndGet();

        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            logger.debug("Exception during request processing, will still record metrics", e);
            throw e;
        } finally {
            try {
                recordMetrics(httpRequest, httpResponse, startTime);
            } catch (Exception e) {
                logger.error("Failed to record HTTP metrics", e);
            } finally {
                inFlightRequestsCounter.decrementAndGet();
                requestMetricsHolder.remove();
            }
        }
    }

    private void recordMetrics(HttpServletRequest request, HttpServletResponse response, long startTime) {
        try {
            // Extract labels
            String method = normalizeHttpMethod(request.getMethod());
            String path = extractRoutePath(request);
            String status = normalizeStatusCode(response.getStatus());

            // Calculate duration in milliseconds (KEY INSIGHT: works better than nanoseconds)
            long durationNanos = System.nanoTime() - startTime;
            long durationMillis = durationNanos / 1_000_000L;

            // Record counter metric
            meterRegistry.counter("http_requests_total",
                    "method", method,
                    "path", path,
                    "status", status)
                    .increment();

            // Record timer metric with explicit histogram configuration
            // CRITICAL: Use millisecond-based durations for better cross-platform consistency
            Timer timer = Timer.builder("http_request_duration")
                    .description("Duration of HTTP requests")
                    .serviceLevelObjectives(
                            Duration.ofMillis(5),     // 0.005 seconds (5ms)
                            Duration.ofMillis(10),    // 0.01 seconds (10ms)
                            Duration.ofMillis(25),    // 0.025 seconds (25ms)
                            Duration.ofMillis(50),    // 0.05 seconds (50ms)
                            Duration.ofMillis(100),   // 0.1 seconds (100ms)
                            Duration.ofMillis(250),   // 0.25 seconds (250ms)
                            Duration.ofMillis(500),   // 0.5 seconds (500ms)
                            Duration.ofMillis(1000),  // 1 second
                            Duration.ofMillis(2000),  // 2 seconds
                            Duration.ofMillis(5000),  // 5 seconds
                            Duration.ofMillis(10000)  // 10 seconds
                    )
                    .maximumExpectedValue(Duration.ofMillis(20000))
                    .publishPercentileHistogram(false)
                    .tag("method", method)
                    .tag("path", path)
                    .tag("status", status)
                    .register(meterRegistry);
            
            // Record duration in milliseconds (KEY INSIGHT: more reliable than nanoseconds)
            timer.record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            logger.error("Error recording HTTP metrics", e);
        }
    }

    private String extractRoutePath(HttpServletRequest request) {
        try {
            // Try to get the route pattern from Spring MVC handler mapping
            Object bestMatchingPattern = request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
            if (bestMatchingPattern != null) {
                return bestMatchingPattern.toString();
            }

            // Try to get the path within handler mapping
            Object pathWithinHandlerMapping = request.getAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping");
            if (pathWithinHandlerMapping != null) {
                return pathWithinHandlerMapping.toString();
            }

            // Fallback to request URI with basic parameter normalization
            String requestURI = request.getRequestURI();
            if (requestURI != null) {
                return normalizePathParameters(requestURI);
            }

            return "unknown";
        } catch (Exception e) {
            logger.debug("Failed to extract route path, using fallback", e);
            return "unknown";
        }
    }

    private String normalizePathParameters(String path) {
        if (path == null) {
            return "unknown";
        }

        // Replace numeric IDs (e.g., /api/users/123 -> /api/users/{id})
        path = path.replaceAll("/\\\\d+", "/{id}");
        
        // Replace UUIDs (e.g., /api/users/550e8400-e29b-41d4-a716-446655440000 -> /api/users/{uuid})
        path = path.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/{uuid}");
        
        return path;
    }

    private String normalizeHttpMethod(String method) {
        if (method == null) {
            return "UNKNOWN";
        }
        return method.toUpperCase();
    }

    private String normalizeStatusCode(int statusCode) {
        return String.valueOf(statusCode);
    }

    private static class RequestMetrics {
        private final long startTimeNanos;
        private final boolean inFlight;

        public RequestMetrics(long startTimeNanos, boolean inFlight) {
            this.startTimeNanos = startTimeNanos;
            this.inFlight = inFlight;
        }

        public long getStartTimeNanos() {
            return startTimeNanos;
        }

        public boolean isInFlight() {
            return inFlight;
        }
    }
}
```

### 5. Testing (Comprehensive Test Suite)

```java
package com.example.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpMetricsFilterTest {

    @Mock
    private FilterChain filterChain;

    private SimpleMeterRegistry meterRegistry;
    private AtomicInteger inFlightRequestsCounter;
    private HttpMetricsFilter httpMetricsFilter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        inFlightRequestsCounter = new AtomicInteger(0);
        httpMetricsFilter = new HttpMetricsFilter(meterRegistry, inFlightRequestsCounter);
    }

    @Test
    void testDoFilter_SuccessfulRequest() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // Act
        httpMetricsFilter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertEquals(0, inFlightRequestsCounter.get());
        
        // Verify metrics were recorded
        assertNotNull(meterRegistry.find("http_requests_total").counter());
        assertNotNull(meterRegistry.find("http_request_duration").timer());
        
        assertTrue(meterRegistry.find("http_requests_total").counter().count() > 0);
    }

    @Test
    void testInFlightTracking() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // Mock filter chain to check in-flight counter during processing
        doAnswer(invocation -> {
            assertEquals(1, inFlightRequestsCounter.get());
            return null;
        }).when(filterChain).doFilter(request, response);

        // Act
        httpMetricsFilter.doFilter(request, response, filterChain);

        // Assert
        assertEquals(0, inFlightRequestsCounter.get());
    }

    @Test
    void testDoFilter_WithException() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        // Act & Assert
        assertThrows(ServletException.class, () -> {
            httpMetricsFilter.doFilter(request, response, filterChain);
        });

        // Verify metrics are still recorded and in-flight counter is decremented
        assertEquals(0, inFlightRequestsCounter.get());
        assertTrue(meterRegistry.find("http_requests_total").counter().count() > 0);
    }
}
```

## Common Pitfalls and Solutions

### 1. Unit Conversion Issues
**Problem**: Different behavior between Prometheus direct scraping and OpenTelemetry Collector export.
**Solution**: Accept that units will display differently but focus on functional accuracy. Use millisecond-based Duration objects for service level objectives.

### 2. MeterFilter Conflicts
**Problem**: MeterFilter registration can interfere with explicit Timer configuration.
**Solution**: Avoid MeterFilter for histogram configuration; use explicit Timer.builder() in the filter instead.

### 3. Thread Safety
**Problem**: Metrics recording can interfere between concurrent requests.
**Solution**: Use thread-local storage for request-specific data and ensure proper cleanup in finally blocks.

### 4. Servlet API Version
**Problem**: Spring Boot 3+ requires Jakarta EE, not Java EE.
**Solution**: Use `jakarta.servlet.*` imports instead of `javax.servlet.*`.

### 5. Path Parameter Explosion
**Problem**: URLs with IDs create too many unique metric series.
**Solution**: Implement path normalization to replace IDs with placeholders like `{id}` and `{uuid}`.

### 6. Timing Precision
**Problem**: Nanosecond precision can cause issues with different export formats.
**Solution**: Calculate in nanoseconds for accuracy but record in milliseconds for better cross-platform consistency.

## What NOT to Do (Lessons Learned)

### ❌ Don't Use MeterFilter for Histogram Configuration
```java
// DON'T DO THIS - causes conflicts
@Bean
public MeterFilter customHistogramFilter() {
    return MeterFilter.configure(Meter.Id.of("http_request_duration"), 
        DistributionStatisticConfig.builder()
            .serviceLevelObjectives(Duration.ofMillis(5), Duration.ofMillis(10))
            .build());
}
```

### ❌ Don't Try to Force Unit Standardization
```java
// DON'T DO THIS - MicrometerConfig attempts don't work reliably
meterRegistry.config().meterFilter(
    MeterFilter.commonTags(Tags.of("unit", "seconds"))
);
```

### ❌ Don't Use Nanosecond Recording
```java
// DON'T DO THIS - can cause display issues
timer.record(durationNanos, TimeUnit.NANOSECONDS);
```

### ✅ Do Use Millisecond Recording
```java
// DO THIS - more reliable across platforms
long durationMillis = durationNanos / 1_000_000L;
timer.record(durationMillis, TimeUnit.MILLISECONDS);
```

## Metrics Endpoints

- **Direct Prometheus**: `/actuator/prometheus` (recommended for Prometheus scraping)
- **JSON Format**: `/actuator/metrics` (for debugging individual metrics)
- **Health Check**: `/actuator/health` (for basic service validation)

## Expected Metrics Output

### Prometheus Format
```
# Counter
http_requests_total{method="GET",path="/api/users/{id}",status="200"} 42

# Timer (with histogram buckets)
http_request_duration_bucket{method="GET",path="/api/users/{id}",status="200",le="0.005"} 15
http_request_duration_bucket{method="GET",path="/api/users/{id}",status="200",le="0.01"} 25
http_request_duration_bucket{method="GET",path="/api/users/{id}",status="200",le="0.025"} 35
http_request_duration_bucket{method="GET",path="/api/users/{id}",status="200",le="+Inf"} 42
http_request_duration_count{method="GET",path="/api/users/{id}",status="200"} 42
http_request_duration_sum{method="GET",path="/api/users/{id}",status="200"} 0.847

# Gauge
http_requests_in_flight 3
```

## Troubleshooting

### Metrics Not Appearing
1. Check that the filter is registered and has high priority (order=1)
2. Verify that requests are actually going through the filter
3. Ensure proper exception handling doesn't prevent metrics recording
4. Make some HTTP requests - metrics are created lazily

### Wrong Histogram Buckets
1. Verify service level objectives are defined correctly with Duration.ofMillis()
2. Check that explicit Timer configuration is used instead of MeterFilter
3. Ensure Timer.builder() is called in the filter, not pre-registered

### Unit Display Inconsistencies
1. Accept that OpenTelemetry Collector may display different units than direct Prometheus
2. Focus on functional correctness rather than unit label formatting
3. Document the expected behavior for your monitoring setup
4. Test both direct Prometheus scraping and OTLP export to understand the differences

### Performance Issues
1. Ensure thread-local cleanup is working properly
2. Verify path normalization isn't creating too many unique series
3. Check that metrics recording exceptions are caught and logged

## Validation Checklist

Before deploying to production, verify:

- [ ] All three metrics appear in `/actuator/prometheus`
- [ ] Counter increments with each request
- [ ] Timer records reasonable durations
- [ ] Gauge tracks concurrent requests correctly
- [ ] Path normalization works (no ID explosion)
- [ ] Exception handling doesn't break metrics
- [ ] Thread-local cleanup prevents memory leaks
- [ ] Both Prometheus and OTLP exports work (even if units differ)

## Conclusion

This implementation provides robust HTTP metrics collection with proper histogram distribution, in-flight tracking, and label normalization. While unit display may vary between export methods (seconds vs milliseconds), the functional behavior and accuracy remain consistent across different monitoring pipelines.

The key insight is to accept the unit conversion differences as a platform reality rather than fighting them, and focus on delivering accurate, reliable metrics that provide valuable observability into your microservice's HTTP traffic patterns.