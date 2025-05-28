# Supplemental Requirement 2

This document describes a supplemental requirement to integrate the execution service with the trade service. Whenever an execution is updated via the PUT `/api/v1/execution/{id}` endpoint, the execution service should automatically update the corresponding execution in the trade service.

## Requirements

### 1. Integration Trigger

When the execution service receives a PUT request to `/api/v1/execution/{id}` and successfully updates an execution, it must:
1. Retrieve the current version of the corresponding execution from the trade service
2. Update the trade service execution with the new fill information
3. Handle any errors gracefully without affecting the execution service's response

### 2. Trade Service Details

| Property | Value |
|----------|-------|
| Host | `globeco-trade-service` |
| Port | `8082` |
| Base URL | `http://globeco-trade-service:8082` |
| GET Endpoint | `GET /api/v1/executions/{id}` |
| PUT Endpoint | `PUT /api/v1/executions/{id}/fill` |
| OpenAPI Spec | [trade-service-openapi.json](trade-service-openapi.json) |

### 3. API Workflow

1. **GET Current Version**: Before updating, call `GET /api/v1/executions/{id}` to retrieve the current `version` number
2. **PUT Update**: Call `PUT /api/v1/executions/{id}/fill` with the payload containing updated execution information

### 4. Request/Response Examples

#### GET Request to Retrieve Version
```http
GET /api/v1/executions/{id}
Host: globeco-trade-service:8082
```

#### PUT Request Payload
```json
{
    "executionStatus": "PART",
    "quantityFilled": 100.00,
    "version": 2
}
```

#### PUT Response
```json
{
    "id": 11,
    "executionTimestamp": "2025-05-28T14:21:04.628501Z",
    "executionStatus": {
        "id": 5,
        "abbreviation": "PART",
        "description": "Partial fill",
        "version": 1
    },
    "blotter": null,
    "tradeType": {
        "id": 1,
        "abbreviation": "BUY",
        "description": "Buy",
        "version": 1
    },
    "tradeOrder": {
        "id": 2,
        "orderId": 2,
        "portfolioId": "68336010e9e4c11d8524694f",
        "orderType": "BUY       ",
        "securityId": "68336002fe95851f0a2aeda9",
        "quantity": 100000,
        "quantitySent": 0,
        "limitPrice": 0,
        "tradeTimestamp": "2025-05-28T11:51:11.381082Z",
        "blotter": null,
        "submitted": null,
        "version": 11
    },
    "destination": {
        "id": 1,
        "abbreviation": "ML",
        "description": "Merrill Lynch",
        "version": 1
    },
    "quantityOrdered": "2500.00",
    "quantityPlaced": "2500.00",
    "quantityFilled": "100.00",
    "limitPrice": "0.00",
    "version": 3,
    "executionServiceId": 10
}
```

### 5. Field Mapping

| Execution Service Field | Trade Service Payload Field | Source | Description |
|------------------------|----------------------------|---------|-------------|
| `trade_service_execution_id` | `{id}` (URL path) | Database | Used as the execution ID in trade service URLs |
| `execution_status` | `executionStatus` | Database | Current execution status ("PART", "FULL", etc.) |
| `quantity_filled` | `quantityFilled` | Database | Total quantity filled for this execution |
| N/A | `version` | GET response | Retrieved from trade service before PUT |

### 6. Error Handling

- **404 Not Found**: If the trade service execution doesn't exist, log the error but don't fail the execution service update
- **409 Conflict**: If optimistic locking fails, retry the GET/PUT sequence once
- **Network Errors**: Log the error but don't fail the execution service update
- **Other HTTP Errors**: Log the error but don't fail the execution service update
- **Timeout**: Configure reasonable timeouts (e.g., 5 seconds) and handle gracefully

### 7. Configuration

Add the following configuration properties:

```properties
# Trade service integration
trade.service.host=globeco-trade-service
trade.service.port=8082
trade.service.base-url=http://${trade.service.host}:${trade.service.port}
trade.service.timeout=5000
trade.service.retry.enabled=true
trade.service.retry.max-attempts=2
```

## Implementation Steps

1. **Create Trade Service Client**
   - Implement a `TradeServiceClient` interface and implementation
   - Add HTTP client configuration with timeouts and error handling
   - Implement GET and PUT methods for trade service communication

2. **Create DTOs for Trade Service**
   - Create `TradeServiceExecutionFillDTO` for PUT requests
   - Create response DTOs if needed for type safety

3. **Update ExecutionServiceImpl**
   - Modify the `updateExecution` method to call the trade service after successful database update
   - Implement the GET/PUT workflow with proper error handling
   - Ensure the trade service call doesn't affect the execution service transaction

4. **Add Configuration**
   - Add trade service configuration properties to `application.properties`
   - Create a configuration class for trade service settings

5. **Implement Tests**
   - Unit tests for `TradeServiceClient` with mocked HTTP responses
   - Integration tests for the complete workflow
   - Error handling tests for various failure scenarios

6. **Update Documentation**
   - Update `README.md` with trade service integration details
   - Document configuration properties and error handling behavior

## Notes

- The trade service integration should be **asynchronous** and **non-blocking** to the execution service's primary functionality
- If the trade service is unavailable, the execution service should continue to function normally
- All trade service interactions should be logged for debugging and monitoring
- Consider implementing circuit breaker pattern for resilience
- Tests should use WireMock or similar for mocking trade service responses

