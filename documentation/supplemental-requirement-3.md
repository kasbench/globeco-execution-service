# Supplemental Requirement 3: Enhanced Execution Service APIs

## Overview

This requirement enhances the Execution Service to support more efficient integration with the UI and to improve consistency with other microservices in the GlobeCo suite.

## Requirements

### 1. Enhanced GET /api/v1/executions Endpoint

#### 1.1 Core Changes
- **Breaking Change**: Backward compatibility is NOT required. This API is not currently called by any other service.
- Add support for filtering, sorting, and pagination
- Use `offset` and `limit` parameters for pagination (consistent with other GlobeCo services)
- Replace `securityId` field with nested `security` object containing both `securityId` and `ticker`

#### 1.2 Security Service Integration
- Map `securityId` to `ticker` using the Security Service API
- Implement in-memory caching with 5-minute TTL using Caffeine (already in build.gradle)
- Reference: [SECURITY_SERVICE_API_GUIDE.md](SECURITY_SERVICE_API_GUIDE.md)
- API Spec: [globeco-security-service-openapi.yaml](globeco-security-service-openapi.yaml)

#### 1.3 Query Parameters
| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| offset | Integer | No | Number of records to skip (default: 0) | `offset=10` |
| limit | Integer | No | Maximum records to return (default: 50, max: 100) | `limit=25` |
| executionStatus | String | No | Filter by execution status | `executionStatus=PART` |
| tradeType | String | No | Filter by trade type | `tradeType=BUY` |
| destination | String | No | Filter by destination | `destination=NYSE` |
| securityId | String | No | Filter by security ID | `securityId=SEC123...` |
| sortBy | String | No | Comma-separated list of fields to sort by (default: id). Prefix with minus (-) for descending order. | `sortBy=receivedTimestamp,-quantity,id` |

#### 1.4 Sorting Specification
- **Multi-field sorting**: Support multiple sort fields separated by commas
- **Default order**: Ascending (ASC) for all fields unless specified otherwise
- **Descending order**: Prefix field name with minus sign (-) for descending order
- **Sort priority**: Fields are applied in the order specified (left to right)
- **Valid sort fields**: `id`, `executionStatus`, `tradeType`, `destination`, `quantity`, `limitPrice`, `receivedTimestamp`, `sentTimestamp`, `quantityFilled`, `averagePrice`

**Examples**:
- `sortBy=id` → Sort by ID ascending (default)
- `sortBy=-receivedTimestamp` → Sort by received timestamp descending
- `sortBy=executionStatus,receivedTimestamp` → Sort by status ascending, then timestamp ascending
- `sortBy=executionStatus,-receivedTimestamp,id` → Sort by status ascending, then timestamp descending, then ID ascending

#### 1.5 Response Structure
```json
{
  "content": [
    {
      "id": 1,
      "executionStatus": "PART",
      "tradeType": "BUY",
      "destination": "NYSE",
      "security": {
        "securityId": "SEC123456789012345678901",
        "ticker": "AAPL"
      },
      "quantity": 10.00000000,
      "limitPrice": 100.00000000,
      "receivedTimestamp": "2025-01-27T15:30:00Z",
      "sentTimestamp": "2025-01-27T15:30:01Z",
      "tradeServiceExecutionId": 1,
      "quantityFilled": 4.50000000,
      "averagePrice": 105.25000000,
      "version": 2
    }
  ],
  "pagination": {
    "offset": 0,
    "limit": 50,
    "totalElements": 150,
    "totalPages": 3,
    "currentPage": 0,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 2. New POST /api/v1/executions/batch Endpoint

#### 2.1 Purpose
Accept multiple executions in a single request for improved performance and reduced network overhead.

#### 2.2 Request Limits
- Maximum 100 executions per request
- Return HTTP 413 (Payload Too Large) if limit exceeded

#### 2.3 Request Body
```json
{
  "executions": [
    {
      "executionStatus": "NEW",
      "tradeType": "BUY",
      "destination": "NYSE",
      "securityId": "SEC123456789012345678901",
      "quantity": 10.00000000,
      "limitPrice": 100.00000000,
      "tradeServiceExecutionId": 1,
      "version": 1
    },
    {
      "executionStatus": "NEW",
      "tradeType": "SELL",
      "destination": "NASDAQ",
      "securityId": "SEC987654321098765432109",
      "quantity": 5.00000000,
      "limitPrice": null,
      "tradeServiceExecutionId": 2,
      "version": 1
    }
  ]
}
```

#### 2.4 Response Body
```json
{
  "status": "PARTIAL_SUCCESS",
  "message": "1 of 2 executions processed successfully",
  "totalRequested": 2,
  "successful": 1,
  "failed": 1,
  "results": [
    {
      "requestIndex": 0,
      "status": "SUCCESS",
      "message": "Execution created successfully",
      "execution": {
        "id": 1,
        "executionStatus": "NEW",
        "tradeType": "BUY",
        "destination": "NYSE",
        "security": {
          "securityId": "SEC123456789012345678901",
          "ticker": "AAPL"
        },
        "quantity": 10.00000000,
        "limitPrice": 100.00000000,
        "receivedTimestamp": "2025-01-27T15:30:00Z",
        "sentTimestamp": "2025-01-27T15:30:01Z",
        "tradeServiceExecutionId": 1,
        "quantityFilled": 0.00000000,
        "averagePrice": null,
        "version": 1
      }
    },
    {
      "requestIndex": 1,
      "status": "FAILED",
      "message": "Invalid security ID format",
      "execution": null
    }
  ]
}
```

#### 2.5 HTTP Status Codes
| Status Code | Condition |
|-------------|-----------|
| 200 | All executions successful |
| 207 | Partial success (some failed) |
| 400 | All executions failed OR validation errors |
| 413 | Payload too large (>100 executions) |
| 500 | Internal server error |

## Data Transfer Object Designs

### 3.1 Modified ExecutionDTO
```java
public class ExecutionDTO {
    private Integer id;
    private String executionStatus;
    private String tradeType;
    private String destination;
    private SecurityDTO security;  // Changed from String securityId
    private BigDecimal quantity;
    private BigDecimal limitPrice;
    private OffsetDateTime receivedTimestamp;
    private OffsetDateTime sentTimestamp;
    private Integer tradeServiceExecutionId;
    private BigDecimal quantityFilled;
    private BigDecimal averagePrice;
    private Integer version;
    
    // constructors, getters, setters, equals, hashCode
}
```

### 3.2 New SecurityDTO
```java
public class SecurityDTO {
    private String securityId;
    private String ticker;
    
    public SecurityDTO() {}
    
    public SecurityDTO(String securityId, String ticker) {
        this.securityId = securityId;
        this.ticker = ticker;
    }
    
    // getters, setters, equals, hashCode
}
```

### 3.3 New ExecutionPageDTO
```java
public class ExecutionPageDTO {
    private List<ExecutionDTO> content;
    private PaginationDTO pagination;
    
    // constructors, getters, setters
}
```

### 3.4 New PaginationDTO
```java
public class PaginationDTO {
    private Integer offset;
    private Integer limit;
    private Long totalElements;
    private Integer totalPages;
    private Integer currentPage;
    private Boolean hasNext;
    private Boolean hasPrevious;
    
    // constructors, getters, setters
}
```

### 3.5 New BatchExecutionRequestDTO
```java
public class BatchExecutionRequestDTO {
    private List<ExecutionPostDTO> executions;
    
    // constructors, getters, setters
    
    @AssertTrue(message = "Maximum 100 executions allowed per batch")
    public boolean isValidSize() {
        return executions == null || executions.size() <= 100;
    }
}
```

### 3.6 New BatchExecutionResponseDTO
```java
public class BatchExecutionResponseDTO {
    private String status;  // "SUCCESS", "PARTIAL_SUCCESS", "FAILED"
    private String message;
    private Integer totalRequested;
    private Integer successful;
    private Integer failed;
    private List<ExecutionResultDTO> results;
    
    // constructors, getters, setters
}
```

### 3.7 New ExecutionResultDTO
```java
public class ExecutionResultDTO {
    private Integer requestIndex;
    private String status;  // "SUCCESS", "FAILED"
    private String message;
    private ExecutionDTO execution;  // null if failed
    
    // constructors, getters, setters
}
```

## Service Integration

### 4.1 Security Service Details
| Service | Host | Port | OpenAPI Spec | Guide |
| --- | --- | --- | --- |  --- |
| Security Service | globeco-security-service | 8000 | [globeco-security-service-openapi.yaml](globeco-security-service-openapi.yaml) | [SECURITY_SERVICE_API_GUIDE.md](SECURITY_SERVICE_API_GUIDE.md) |

### 4.2 Caching Strategy
- Use Caffeine for in-memory caching of security ID to ticker mappings
- Cache TTL: 5 minutes
- Cache key: `security:{securityId}`
- Cache value: `ticker` string
- Handle cache misses by calling Security Service API
- Log cache hit/miss statistics for monitoring

## Implementation Plan

### Phase 1: Security Service Integration
- [x] Create `SecurityDTO` class
- [x] Create `SecurityServiceClient` interface
- [x] Implement `SecurityServiceClientImpl` with RestTemplate/WebClient
- [x] Configure Caffeine cache for security mappings
- [x] Add cache configuration properties
- [x] Write unit tests for security service client
- [x] Write integration tests with WireMock

### Phase 2: Enhanced GET Endpoint
- [x] Create `PaginationDTO` class
- [x] Create `ExecutionPageDTO` class
- [x] Modify `ExecutionDTO` to use `SecurityDTO` instead of `securityId`
- [x] Update `ExecutionRepository` with query methods for filtering and multi-field sorting
- [x] Implement pagination support in repository layer
- [x] Add parsing logic for comma-separated sortBy parameter with minus prefix handling
- [x] Modify `ExecutionService.findAll()` to support filtering and pagination
- [x] Update `ExecutionController.getAllExecutions()` with query parameters
- [x] Add request validation for query parameters
- [x] Write unit tests for new repository methods
- [x] Write integration tests for enhanced GET endpoint

### Phase 3: Batch POST Endpoint ✅ COMPLETED
- [x] Create `BatchExecutionRequestDTO` class
- [x] Create `BatchExecutionResponseDTO` class
- [x] Create `ExecutionResultDTO` class
- [x] Add validation annotations to `BatchExecutionRequestDTO`
- [x] Implement `ExecutionService.createBatchExecutions()` method
- [x] Add transactional batch processing logic
- [x] Implement partial success handling
- [x] Add `ExecutionController.createBatchExecutions()` endpoint
- [x] Implement proper error handling and status codes
- [x] Write unit tests for batch service method
- [x] Write integration tests for batch POST endpoint

### Phase 4: Database Optimizations
- [ ] Add database indexes for common filter fields
- [ ] Optimize queries for large datasets
- [ ] Add database migration scripts
- [ ] Performance test with large datasets
- [ ] Monitor query performance metrics

### Phase 5: Testing & Documentation
- [ ] Update OpenAPI specification
- [ ] Add comprehensive integration tests
- [ ] Performance testing for batch operations
- [ ] Update API documentation
- [ ] Create migration guide for breaking changes
- [ ] Add monitoring and logging enhancements

### Phase 6: Deployment
- [ ] Update configuration properties
- [ ] Deploy to development environment
- [ ] Validate functionality in dev environment
- [ ] Update deployment scripts
- [ ] Deploy to staging environment
- [ ] Performance validation in staging
- [ ] Deploy to production environment

## Success Criteria

- [ ] Enhanced GET endpoint supports all specified query parameters
- [ ] Pagination works correctly with proper metadata
- [ ] Security service integration provides ticker information
- [ ] Caching reduces Security Service API calls by >80%
- [ ] Batch POST endpoint handles up to 100 executions
- [ ] Partial success scenarios return appropriate HTTP status codes
- [ ] All existing functionality remains intact
- [ ] Performance improves by at least 50% for bulk operations
- [ ] 100% test coverage for new functionality
- [ ] Zero downtime deployment achieved

