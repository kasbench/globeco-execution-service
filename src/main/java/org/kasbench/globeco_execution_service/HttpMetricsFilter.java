package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.Counter;
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
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("/\\d+(?=/|$)");
    private static final Pattern UUID_PATTERN = Pattern
            .compile("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");

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
            // Extract labels
            String method = normalizeMethod(request.getMethod());
            String path = normalizePath(request);
            String status = String.valueOf(response.getStatus());

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
     */
    private String normalizeMethod(String method) {
        return method != null ? method.toUpperCase() : "UNKNOWN";
    }

    /**
     * Normalizes request path by replacing numeric IDs and UUIDs with placeholders
     * to prevent metric cardinality explosion.
     */
    private String normalizePath(HttpServletRequest request) {
        try {
            String path = request.getRequestURI();
            if (path == null) {
                return "/unknown";
            }

            // Remove context path if present
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }

            // Replace numeric IDs with {id} placeholder
            path = NUMERIC_ID_PATTERN.matcher(path).replaceAll("/{id}");

            // Replace UUIDs with {uuid} placeholder
            path = UUID_PATTERN.matcher(path).replaceAll("/{uuid}");

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

}