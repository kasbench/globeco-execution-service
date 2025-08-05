package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    // Patterns for path normalization to prevent cardinality explosion
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("/\\d+(?=/|$)");
    private static final Pattern UUID_PATTERN = Pattern.compile("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");

    private final MeterRegistry meterRegistry;
    private final AtomicInteger inFlightCounter;

    // ThreadLocal for request-specific timing data
    private static final ThreadLocal<RequestMetrics> REQUEST_METRICS = new ThreadLocal<>();

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
        long startTimeNanos = System.nanoTime();
        boolean inFlightIncremented = false;

        try {
            // Increment in-flight counter
            inFlightCounter.incrementAndGet();
            inFlightIncremented = true;

            // Store request metrics in ThreadLocal
            REQUEST_METRICS.set(new RequestMetrics(startTimeNanos, true));

            // Process the request
            chain.doFilter(request, response);

        } catch (Exception e) {
            // Record metrics even when exceptions occur
            recordMetrics(httpRequest, httpResponse, startTimeNanos);
            throw e;
        } finally {
            try {
                // Always record metrics and decrement in-flight counter
                recordMetrics(httpRequest, httpResponse, startTimeNanos);

                if (inFlightIncremented) {
                    inFlightCounter.decrementAndGet();
                }
            } catch (Exception e) {
                logger.error("Failed to record HTTP metrics", e);
            } finally {
                // Always clean up ThreadLocal to prevent memory leaks
                REQUEST_METRICS.remove();
            }
        }
    }

    /**
     * Records HTTP request metrics including counter increment and duration timing.
     * Handles all exceptions to ensure service reliability.
     */
    private void recordMetrics(HttpServletRequest request, HttpServletResponse response, long startTimeNanos) {
        try {
            String method = normalizeMethod(request.getMethod());
            String path = normalizePath(request);
            String status = String.valueOf(response.getStatus());

            // Record request counter
            Counter.builder(HTTP_REQUESTS_TOTAL)
                    .tag("method", method)
                    .tag("path", path)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();

            // Record request duration
            long durationNanos = System.nanoTime() - startTimeNanos;
            Timer.builder(HTTP_REQUEST_DURATION)
                    .tag("method", method)
                    .tag("path", path)
                    .tag("status", status)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

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

    /**
     * Internal class for storing request-specific metrics data in ThreadLocal.
     */
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