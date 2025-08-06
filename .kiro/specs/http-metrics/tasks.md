# Implementation Plan

- [x] 1. Add required Micrometer dependencies to build.gradle
  - Add micrometer-registry-prometheus dependency for Prometheus format export
  - Verify existing OTLP dependencies are sufficient
  - _Requirements: 4.1, 4.2_

- [x] 2. Create HttpMetricsConfiguration class with basic metric registration
  - Implement Spring configuration class with @Configuration annotation
  - Register Counter, Timer, and Gauge beans with MeterRegistry
  - Create AtomicInteger bean for in-flight request tracking
  - Pre-register metrics with sample tags for immediate visibility
  - _Requirements: 1.1, 2.1, 3.1, 5.5_

- [x] 3. Implement core HttpMetricsFilter with essential functionality
  - Create servlet filter implementing Filter interface with Jakarta EE imports
  - Implement doFilter method with timing, in-flight tracking, and metric recording
  - Add basic path normalization for ID replacement to prevent cardinality explosion
  - Include comprehensive exception handling to ensure service reliability
  - Use ThreadLocal for request-specific data with proper cleanup
  - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.4, 3.1, 3.2, 5.1, 5.2, 5.4, 6.1, 6.2, 6.3_

- [x] 4. Register HttpMetricsFilter with Spring Boot filter registration
  - Add FilterRegistrationBean configuration in HttpMetricsConfiguration
  - Set high priority (order=1) and URL pattern "/*" for comprehensive coverage
  - Ensure filter captures all HTTP traffic including API endpoints and actuator endpoints
  - _Requirements: 6.1, 6.4_

- [x] 5. Configure histogram buckets optimized for millisecond OTLP export
  - Implement explicit Timer.builder() configuration in the filter
  - Use millisecond-based service level objectives: [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000]
  - Record durations in milliseconds for OpenTelemetry Collector compatibility
  - _Requirements: 2.3, 2.5_

- [x] 6. Implement label extraction and normalization logic
  - Add method normalization to uppercase HTTP methods
  - Implement path extraction using Spring MVC handler mapping attributes
  - Add status code string conversion
  - Include path parameter replacement for numeric IDs and UUIDs
  - _Requirements: 1.3, 1.4, 1.5, 6.5_

- [ ] 7. Deploy and verify metrics endpoint functionality in Kubernetes
  - Build and deploy service to Kubernetes environment
  - Verify /actuator/prometheus endpoint returns HTTP metrics
  - Test that metrics appear after making HTTP requests to service endpoints
  - Confirm OTLP export to OpenTelemetry Collector is working
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 8. Create focused unit tests for HttpMetricsFilter core functionality
  - Test successful request metric recording with mock HttpServletRequest/Response
  - Test in-flight counter increment/decrement accuracy
  - Test exception handling scenarios to ensure metrics are recorded before propagation
  - Test ThreadLocal cleanup to prevent memory leaks
  - _Requirements: 5.1, 5.2, 5.4_

- [ ] 9. Create unit tests for HttpMetricsConfiguration
  - Test bean registration and proper dependency injection
  - Verify metric pre-registration with sample tags
  - Test filter registration configuration including order and URL patterns
  - _Requirements: 5.5_

- [ ] 10. Add integration tests for metrics endpoint
  - Create test that makes actual HTTP requests and verifies metric values
  - Test Prometheus format output parsing and validation
  - Verify metric labels contain expected values (method, path, status)
  - Test histogram bucket distribution with various request durations
  - _Requirements: 4.3, 1.2, 2.4_

- [ ] 11. Create performance and load testing
  - Implement test to measure filter overhead under concurrent load
  - Test thread safety with multiple concurrent requests
  - Verify path normalization prevents metric cardinality explosion
  - Test metric recording accuracy under high request volume
  - _Requirements: 5.3, 5.4, 6.5_

- [ ] 12. Add comprehensive error handling tests
  - Test metric recording failure scenarios with proper error logging
  - Test request processing with various exception types
  - Verify service continues normal operation when metrics fail
  - Test edge cases like malformed requests and unusual HTTP methods
  - _Requirements: 5.1, 5.2, 6.2, 6.3_