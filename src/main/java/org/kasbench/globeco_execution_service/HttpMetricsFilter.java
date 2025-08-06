package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Servlet filter that records HTTP request metrics including request count,
 * duration, and in-flight requests. Implements comprehensive error handling
 * and path normalization to prevent metric cardinality explosion.
 */
@Component
public class HttpMetricsFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(HttpMetricsFilter.class);

    private static final String HTTP_REQUESTS_TOTAL = "http_requests_total";
    private static final String HTTP_REQUEST_DURATION = "http_request_duration";

    // Histogram buckets optimized for millisecond OTLP export
    // Service level objectives: [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000,
    // 10000] milliseconds
    private static final Duration[] HISTOGRAM_BUCKETS = {
            Duration.ofMillis(5),
            Duration.ofMillis(10),
            Duration.ofMillis(25),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(250),
            Duration.ofMillis(500),
            Duration.ofMillis(1000),
            Duration.ofMillis(2500),
            Duration.ofMillis(5000),
            Duration.ofMillis(10000)
    };

    // Cache for Timer instances to avoid re-registration
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    // Patterns for path normalization to prevent cardinality explosion
    // Matches numeric IDs (e.g., /123, /456/) but excludes version numbers (e.g., /v1, /api)
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("/\\d{2,}(?=/|$)");
    
    // Matches standard UUID format: 8-4-4-4-12 hexadecimal characters
    private static final Pattern UUID_PATTERN = Pattern
            .compile("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");
    
    // Additional pattern for simple numeric IDs that might be single digit
    private static final Pattern SINGLE_DIGIT_ID_PATTERN = Pattern.compile("/\\d(?=/|$)");

    private final MeterRegistry meterRegistry;
    private final AtomicInteger inFlightCounter;

    public HttpMetricsFilter(MeterRegistry meterRegistry, AtomicInteger httpRequestsInFlightCounter) {
        this.meterRegistry = meterRegistry;
        this.inFlightCounter = httpRequestsInFlightCounter;
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
        boolean inFlightIncremented = false;

        try {
            // Increment in-flight counter
            inFlightCounter.incrementAndGet();
            inFlightIncremented = true;

            // Process the request
            chain.doFilter(request, response);

        } catch (Exception e) {
            // Record metrics even when exceptions occur
            recordMetrics(httpRequest, httpResponse, startTime);
            throw e;
        } finally {
            try {
                // Always record metrics and decrement in-flight counter
                recordMetrics(httpRequest, httpResponse, startTime);

                if (inFlightIncremented) {
                    inFlightCounter.decrementAndGet();
                }
            } catch (Exception e) {
                logger.error("Failed to record HTTP metrics", e);
            }
        }
    }

    /**
     * Records HTTP request metrics including counter increment and duration timing.
     * Handles all exceptions to ensure service reliability.
     */
    private void recordMetrics(HttpServletRequest request, HttpServletResponse response, long startTime) {
        try {
            // Extract and normalize labels
            String method = normalizeMethod(request.getMethod());
            String path = normalizePath(request);
            String status = normalizeStatusCode(response.getStatus());

            // Calculate duration in milliseconds (KEY INSIGHT: works better than
            // nanoseconds)
            long durationNanos = System.nanoTime() - startTime;
            long durationMillis = durationNanos / 1_000_000L;

            // Record counter metric
            meterRegistry.counter(HTTP_REQUESTS_TOTAL,
                    "method", method,
                    "path", path,
                    "status", status)
                    .increment();

            // Record timer metric with cached Timer instances to avoid re-registration
            String timerKey = HTTP_REQUEST_DURATION + ":" + method + ":" + path + ":" + status;
            Timer timer = timerCache.computeIfAbsent(timerKey, key -> Timer.builder(HTTP_REQUEST_DURATION)
                    .description("Duration of HTTP requests")
                    .serviceLevelObjectives(HISTOGRAM_BUCKETS)
                    .publishPercentileHistogram(false)
                    .tag("method", method)
                    .tag("path", path)
                    .tag("status", status)
                    .register(meterRegistry));

            // Record duration in milliseconds
            timer.record(durationMillis, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            logger.error("Failed to record HTTP request metrics for {} {}",
                    request.getMethod(), request.getRequestURI(), e);
        }
    }

    /**
     * Normalizes HTTP method to uppercase format.
     * Handles null/empty methods and ensures consistent formatting.
     */
    private String normalizeMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return "UNKNOWN";
        }
        return method.trim().toUpperCase();
    }

    /**
     * Normalizes HTTP status code to string format.
     * Converts numeric status codes to strings as required by the metrics specification.
     */
    private String normalizeStatusCode(int statusCode) {
        // Convert numeric HTTP status codes to strings ("200", "404", "500")
        // Handle edge cases where status might be 0 or invalid
        if (statusCode <= 0) {
            return "0";
        }
        return String.valueOf(statusCode);
    }

    /**
     * Normalizes request path by extracting Spring MVC handler mapping attributes
     * when available, or falling back to URI normalization with ID/UUID replacement
     * to prevent metric cardinality explosion.
     */
    private String normalizePath(HttpServletRequest request) {
        try {
            // First try to get the best match pattern from Spring MVC handler mapping
            String bestMatchingPattern = extractSpringMvcPattern(request);
            if (bestMatchingPattern != null && !bestMatchingPattern.isEmpty()) {
                return bestMatchingPattern;
            }

            // Fallback to URI normalization
            String path = request.getRequestURI();
            if (path == null) {
                return "/unknown";
            }

            // Remove context path if present
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }

            // Apply path parameter replacement for numeric IDs and UUIDs
            path = normalizePathParameters(path);

            // Ensure path starts with /
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            return path;

        } catch (Exception e) {
            logger.warn("Failed to normalize path for request {}, using fallback",
                    request.getRequestURI(), e);
            return "/unknown";
        }
    }

    /**
     * Extracts Spring MVC handler mapping pattern from request attributes.
     * This provides the most accurate route pattern like "/api/executions/{id}".
     */
    private String extractSpringMvcPattern(HttpServletRequest request) {
        try {
            // Try to get the best matching pattern from Spring MVC
            Object bestMatchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (bestMatchingPattern instanceof String) {
                return (String) bestMatchingPattern;
            }

            // Fallback to path within handler mapping
            Object pathWithinHandlerMapping = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            if (pathWithinHandlerMapping instanceof String) {
                return (String) pathWithinHandlerMapping;
            }

            return null;
        } catch (Exception e) {
            logger.debug("Failed to extract Spring MVC pattern from request attributes", e);
            return null;
        }
    }

    /**
     * Normalizes path parameters by replacing numeric IDs and UUIDs with placeholders.
     * This prevents metric cardinality explosion while maintaining meaningful path patterns.
     */
    private String normalizePathParameters(String path) {
        if (path == null) {
            return "/unknown";
        }

        // Replace UUIDs first (more specific pattern)
        // Matches standard UUID format: 8-4-4-4-12 hexadecimal characters
        path = UUID_PATTERN.matcher(path).replaceAll("/{uuid}");

        // Replace multi-digit numeric IDs with {id} placeholder
        // Matches patterns like /123, /456/ but excludes single digits like /v1
        path = NUMERIC_ID_PATTERN.matcher(path).replaceAll("/{id}");

        // Replace single digit IDs only if they appear to be IDs (not version numbers)
        // This is more conservative to avoid replacing legitimate single-digit paths
        if (path.matches(".*/\\d$") || path.matches(".*/\\d/.*")) {
            // Only replace if it looks like an ID context (after common API paths)
            if (path.contains("/api/") || path.contains("/executions/") || 
                path.contains("/users/") || path.contains("/orders/")) {
                path = SINGLE_DIGIT_ID_PATTERN.matcher(path).replaceAll("/{id}");
            }
        }

        return path;
    }

}