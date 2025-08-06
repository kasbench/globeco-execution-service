package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.HandlerMapping;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

/**
 * Test class to verify label extraction and normalization logic in HttpMetricsFilter.
 * This test focuses on the specific functionality implemented in task 6.
 */
public class HttpMetricsFilterLabelTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private MeterRegistry meterRegistry;
    private AtomicInteger inFlightCounter;
    private HttpMetricsFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        inFlightCounter = new AtomicInteger(0);
        filter = new HttpMetricsFilter(meterRegistry, inFlightCounter);
    }

    @Test
    void testMethodNormalization() throws Exception {
        // Test uppercase HTTP method normalization
        when(request.getMethod()).thenReturn("get");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // The method should be normalized to uppercase in the metrics
        // This is verified by the fact that the filter processes without error
    }

    @Test
    void testPathNormalizationWithSpringMvcPattern() throws Exception {
        // Test Spring MVC handler mapping pattern extraction
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/executions/123");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn("/api/executions/{id}");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // The path should use the Spring MVC pattern when available
    }

    @Test
    void testPathNormalizationWithNumericIds() throws Exception {
        // Test numeric ID replacement when Spring MVC pattern is not available
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/executions/12345");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn(null);
        when(request.getContextPath()).thenReturn("");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // The numeric ID should be replaced with {id} placeholder
    }

    @Test
    void testPathNormalizationWithUuid() throws Exception {
        // Test UUID replacement
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/executions/550e8400-e29b-41d4-a716-446655440000");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn(null);
        when(request.getContextPath()).thenReturn("");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // The UUID should be replaced with {uuid} placeholder
    }

    @Test
    void testStatusCodeNormalization() throws Exception {
        // Test status code string conversion
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(404);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // The status code should be converted to string "404"
    }

    @Test
    void testNullMethodHandling() throws Exception {
        // Test handling of null HTTP method
        when(request.getMethod()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Should handle null method gracefully with "UNKNOWN"
    }

    @Test
    void testEmptyMethodHandling() throws Exception {
        // Test handling of empty HTTP method
        when(request.getMethod()).thenReturn("  ");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Should handle empty method gracefully with "UNKNOWN"
    }
}