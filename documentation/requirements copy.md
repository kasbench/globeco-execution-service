# GlobeCo Trade Service Requirements

## Background

This document provides requirements for the Trade Service.  This service is designed to manage trades as part of a portfolio management application.

This microservice will be deployed on Kubernetes 1.33.

This microservice is part of the GlobeCo suite of applications for benchmarking Kubernetes autoscaling.

Name of service: Trade Service <br>
Host: globeco-trade-service <br>
Port: 8002 <br>

Author: Noah Kriehger <br>
Email: noah@kasbench.org

## Technology

| Technology | Version | Notes |
|---------------------------|----------------|---------------------------------------|
| Java | 21 | |
| Spring Boot | 3.4.5 | |
| Spring Dependency Mgmt | 1.1.7 | Plugin for dependency management |
| Spring Boot Starter Web | (from BOM) | For REST API |
| Spring Boot Starter Data JPA | (from BOM) | For JPA/Hibernate ORM |
| Spring Boot Starter Actuator | (from BOM) | For monitoring/management |
| Flyway Core | (from BOM) | Database migrations |
| Flyway Database PostgreSQL| (from BOM) | PostgreSQL-specific Flyway support |
| PostgreSQL JDBC Driver | (from BOM) | Runtime JDBC driver |
| JUnit Platform Launcher | (from BOM) | For running tests |
| Spring Boot Starter Test | (from BOM) | For testing |
| PostgreSQL (Database) | 17 | As specified in [globeco-trade-service-postgresql](https://github.com/kasbench/globeco-trade-service-postgresql) |

Notes:
- (from BOM) means the version is managed by the Spring Boot BOM (Bill of Materials) and will match the Spring Boot version unless overridden.
- All dependencies are managed via Maven Central.
- The project uses Gradle as the build tool.



## Other services

| Name | Host | Port | Description |
| --- | --- | --- | --- |
Security Service | globeco-security-service | 8000 | Manages securities such as stocks, bonds, and options |
| Order Service | globeco-order-service | 8001 |


## Caching
- Use Spring's caching abstraction for security, blotter, trade_type, trade_status, execution_status
- Caches should have a 5 minute EOL
- Initially, all caches should be with in-memory caching


## Database Information

The database is at globeco-trade-service-postgresql:32800
The database is the default `postgres` database.
The schema is the default `public` schema.
The owner of all database objects is `postgres`.

## Data Dictionary


[Data Dictionary](trade-service.html)



## Entity Relationship Diagram

<img src="./images/trade-service.png">




```
+-------------+       +-------------------+       +-------------+
|   blotter   |<----->|   trade_block     |<----->| trade_type  |
+-------------+       +-------------------+       +-------------+
| PK id       |       | PK id             |       | PK id       |
| abbrev.     |       | blotter_id (FK)   |       | abbrev.     |
| name        |       | trade_status_id   |       | desc.       |
| version     |       | order_type        |       | version     |
+-------------+       | trade_type_id     |       +-------------+
                      | security_id       |       +-------------------+
                      | quantity_ordered  |       | trade_status      |
                      | limit_price       |       +-------------------+
                      | quantity_placed   |<----->| PK id             |
                      | quantity_filled   |       | abbrev.           |
                      | version           |       | desc.             |
                      +-------------------+       | version           |
                                                  +-------------------+
+-------------------+       +-------------------+       +-------------------+
| trade_order       |<----->| trade_block_alloc |<----->| trade_block       |
+-------------------+       +-------------------+       +-------------------+
| PK id             |       | PK id             |       | PK id             |
| portfolio_id      |       | trade_order_id FK |       | ...               |
| quantity          |       | trade_block_id FK |       |                   |
| trade_timestamp   |       | version           |       |                   |
| version           |       +-------------------+       |                   |
+-------------------+                                   +-------------------+
+-------------------+       +-------------------+
| execution         |<----->| execution_status  |
+-------------------+       +-------------------+
| PK id             |       | PK id             |
| trade_block_id FK |       | abbrev.           |
| execution_status  |       | desc.             |
| trade_type_id     |       | version           |
| execution_ts      |       +-------------------+
| quantity_placed   |
| quantity_filled   |
| limit_price       |
| version           |
+-------------------+
```

---

## Tables

### blotter

A blotter is a record of financial transactions, typically used to organize and group trade blocks.

| Column        | Data Type     | Constraints      | Description                       |
|---------------|--------------|------------------|-----------------------------------|
| id            | serial       | PK, NOT NULL     | Unique identifier                 |
| abbreviation  | varchar(20)  | NOT NULL         | Short code for the blotter        |
| name          | varchar(100) | NOT NULL         | Name of the blotter               |
| version       | integer      | NOT NULL, DEF 1  | Optimistic locking version number |
---

#### Initialization data for blotter

| abbreviation | name | version | 
| --- | --- | --- |
| Default | Default | 1 |
| EQ | Equity | 1 |
| FI | Fixed Income | 1 |
| HOLD | Hold | 1 |


---

### trade_order

Represents an order placed by a portfolio.

| Column        | Data Type     | Constraints      | Description                       |
|---------------|--------------|------------------|-----------------------------------|
| id            | serial       | PK, NOT NULL     | Unique identifier                 |
| portfolio_id  | char(24)     | NOT NULL         | ID of the portfolio making order  |
| quantity      | decimal(18,8)| NOT NULL         | Amount of security to trade       |
| trade_timestamp| timestamptz | NOT NULL, DEF NOW| When the order was placed         |
| version       | integer      | NOT NULL, DEF 1  | Optimistic locking version number |

---

### trade_block

Represents a block of trades, which can be allocated to multiple orders.

| Column            | Data Type     | Constraints      | Description                                 |
|-------------------|--------------|------------------|---------------------------------------------|
| id                | serial       | PK, NOT NULL     | Unique identifier                           |
| trade_status_id   | integer      | FK,              | Reference to trade status                   |
| blotter_id        | integer      | FK, NOT NULL     | Reference to the containing blotter         |
| order_type        | varchar(10)  | NOT NULL         | Order type code (redundant with trade_type?)|
| trade_type_id     | integer      | FK, NOT NULL     | Reference to trade type                     |
| security_id       | char(24)     | NOT NULL         | ID of the security being traded             |
| quantity_ordered  | decimal(18,8)| NOT NULL         | Amount of security to trade                 |
| limit_price       | decimal(18,8)| NOT NULL         | Price limit for the trade                   |
| quantity_placed   | decimal(18,8)| NOT NULL, DEF 0  | Amount placed                               |
| quantity_filled   | decimal(18,8)| NOT NULL, DEF 0  | Amount filled                               |
| version           | integer      | NOT NULL, DEF 1  | Optimistic locking version number           |

---

### trade_block_allocation

Allocates portions of a trade block to specific trade orders.

| Column          | Data Type     | Constraints      | Description                       |
|-----------------|--------------|------------------|-----------------------------------|
| id              | serial       | PK, NOT NULL     | Unique identifier                 |
| version         | integer      | NOT NULL, DEF 1  | Optimistic locking version number |
| trade_order_id  | integer      | FK, NOT NULL     | Reference to trade_order          |
| trade_block_id  | integer      | FK, NOT NULL     | Reference to trade_block          |

---

### execution

Represents the execution of a trade block.

| Column              | Data Type     | Constraints      | Description                                 |
|---------------------|--------------|------------------|---------------------------------------------|
| id                  | serial       | PK, NOT NULL     | Unique identifier                           |
| trade_block_id      | integer      | FK, NOT NULL     | Reference to trade_block                    |
| execution_status_id | integer      | FK               | Reference to execution_status               |
| trade_type_id       | integer      | FK               | Reference to trade_type                     |
| execution_timestamp | timestamptz  | NOT NULL, DEF NOW| When the execution occurred                 |
| quantity_placed     | decimal(18,8)| NOT NULL         | Amount placed                               |
| quantity_filled     | decimal(18,8)| NOT NULL, DEF 0  | Amount filled                               |
| limit_price         | decimal(18,8)|                  | Price limit for the execution               |
| version             | integer      | NOT NULL, DEF 1  | Optimistic locking version number           |

---

### trade_type

Defines the various types of trades available in the system.

| Column        | Data Type     | Constraints      | Description                       |
|---------------|--------------|------------------|-----------------------------------|
| id            | serial       | PK, NOT NULL     | Unique identifier                 |
| abbreviation  | varchar(10)  | NOT NULL         | Short code for the trade type     |
| description   | varchar(60)  | NOT NULL         | Detailed description of trade type|
| version       | integer      | NOT NULL, DEF 1  | Optimistic locking version number |

---
#### Initialization data for trade_type

| abbreviation | description | version |
| --- | --- | --- |
| BUY | Buy | 1 |
| SELL | Sell | 1 |
| SHORT | Sell to Open | 1 |
| COVER | Buy to Close | 1 |
| EXRC | Exercise | 1 |

---

### trade_status

Defines the possible statuses a trade block can have.

| Column        | Data Type     | Constraints      | Description                       |
|---------------|--------------|------------------|-----------------------------------|
| id            | serial       | PK, NOT NULL     | Unique identifier                 |
| abbreviation  | varchar(20)  | NOT NULL         | Short code for the status         |
| description   | varchar(60)  | NOT NULL         | Detailed description of status    |
| version       | integer      | NOT NULL, DEF 1  | Optimistic locking version number |

---

#### Initialization data for trade_status

| abbreviation | description | version |
| --- | --- | --- |
| NEW | New | 1 |
| SENT | Sent | 1 |
| WORK | In progress | 1 |
| FULL | Filled | 1 |
| PART | Partial fill | 1 |
| HOLD | Hold | 1 |
| CNCL | Cancel | 1 |
| CNCLD | Cancelled | 1 |
| CPART | Cancelled with partial fill | 1 |
| DEL | Delete | 1 |




---


### execution_status

Defines the possible statuses for an execution.

| Column        | Data Type     | Constraints      | Description                       |
|---------------|--------------|------------------|-----------------------------------|
| id            | serial       | PK, NOT NULL     | Unique identifier                 |
| abbreviation  | varchar(20)  | NOT NULL         | Short code for the status         |
| description   | varchar(60)  | NOT NULL         | Detailed description of status    |
| version       | integer      | NOT NULL, DEF 1  | Optimistic locking version number |

---
#### Initialization data for execution_status

| abbreviation | description | version |
| --- | --- | --- |
| NEW | New | 1 |
| SENT | Sent | 1 |
| WORK | In progress | 1 |
| FULL | Filled | 1 |
| PART | Partial fill | 1 |
| HOLD | Hold | 1 |
| CNCL | Cancel | 1 |
| CNCLD | Cancelled | 1 |
| CPART | Cancelled with partial fill | 1 |
| DEL | Delete | 1 |

--

## Relationships

1. **blotter to trade_block (1:N)**
   - A blotter can contain multiple trade blocks.
   - Each trade block must have exactly one blotter.
   - If a blotter is deleted, referenced trade blocks are restricted from deletion.

2. **trade_block to trade_block_allocation (1:N)**
   - A trade block can be allocated to multiple trade orders.
   - Each allocation references one trade block.

3. **trade_order to trade_block_allocation (1:N)**
   - A trade order can be allocated to multiple trade blocks.
   - Each allocation references one trade order.

4. **trade_block to execution (1:N)**
   - A trade block can have multiple executions.
   - Each execution references one trade block.

5. **trade_type to trade_block (1:N)**
   - A trade type can be used by multiple trade blocks.
   - Each trade block must have exactly one trade type.

6. **trade_type to execution (1:N)**
   - A trade type can be used by multiple executions.
   - Each execution can reference one trade type.

7. **trade_status to trade_block (1:N)**
   - A trade status can apply to multiple trade blocks.
   - Each trade block can reference one trade status.

8. **execution_status to execution (1:N)**
   - An execution status can apply to multiple executions.
   - Each execution can reference one execution status.

---

## Design Notes

1. The database uses PostgreSQL version 17.0.
2. All tables include a version column for optimistic locking.
3. The model uses 24-character strings for external IDs (portfolio_id, security_id), to accommodate MongoDB ObjectIDs.
4. Decimal columns use high precision (18,8) to accommodate financial calculations.
5. Foreign key relationships use `ON DELETE RESTRICT` or `ON DELETE SET NULL` as appropriate to maintain referential integrity.
6. All timestamp fields use timestamptz (timestamp with time zone) to ensure proper timezone handling.

---



## Data Transfer Objects (DTOs)

The following DTOs represent the data structures used to transfer information between the API and clients for the main entities in the GlobeCo Trade Service.

---

Here are the request and response DTOs for `security` and `securityType`, based on the OpenAPI schema in `security-openapi.yaml`. These are styled to match your conventions and are suitable for use in your trade service as request/response models.

---

## SecurityType DTOs

### SecurityTypeDTO for GET and PUT (Response)

Represents a security type.

| Field           | Type    | Nullable | Description                        |
|-----------------|---------|----------|------------------------------------|
| securityTypeId  | String  | No       | Unique identifier for the security type |
| abbreviation    | String  | No       | Short code for the security type   |
| description     | String  | No       | Description of the security type   |
| version         | Integer | No       | Optimistic locking version number  |

---

### SecurityTypeDTO for POST (Request)

| Field        | Type    | Nullable | Description                        |
|--------------|---------|----------|------------------------------------|
| abbreviation | String  | No       | Short code for the security type   |
| description  | String  | No       | Description of the security type   |


---

## Security DTOs

### SecurityDTO 

Represents a security, including its nested security type.

| Field           | Type                | Nullable | Description                        |
|-----------------|---------------------|----------|------------------------------------|
| securityId      | String              | No       | Unique identifier for the security |
| ticker          | String              | No       | Ticker symbol                      |
| description     | String              | No       | Description of the security        |
| securityType    | SecurityTypeNestedDTO | No     | The security type (nested DTO)     |
| version         | Integer             | No       | Optimistic locking version number  |

#### SecurityTypeNestedDTO

| Field           | Type    | Nullable | Description                        |
|-----------------|---------|----------|------------------------------------|
| securityTypeId  | String  | No       | Unique identifier for the security type |
| abbreviation    | String  | No       | Short code for the security type   |
| description     | String  | No       | Description of the security type   |




---


### BlotterDTO for GET and PUT

Represents a blotter, which is a record grouping financial transactions.

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| id           | Integer | No       | Unique identifier for the blotter           |
| abbreviation | String  | No       | Short code for the blotter                  |
| name         | String  | No       | Name of the blotter                         |
| version      | Integer | No       | Optimistic locking version number           |

---

### BlotterDTO for POST

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| abbreviation | String  | No       | Short code for the blotter                  |
| name         | String  | No       | Name of the blotter                         |


---

### TradeTypeDTO for GET and PUT

Represents the type of a trade.

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| id           | Integer | No       | Unique identifier for the trade type        |
| abbreviation | String  | No       | Short code for the trade type               |
| description  | String  | No       | Detailed description of the trade type      |
| version      | Integer | No       | Optimistic locking version number           |

---

### TradeTypeDTO for POST

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| abbreviation | String  | No       | Short code for the trade type               |
| description  | String  | No       | Detailed description of the trade type      |


---

### TradeStatusDTO for GET and PUT

Represents the status of a trade block.

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| id           | Integer | No       | Unique identifier for the trade status      |
| abbreviation | String  | No       | Short code for the status                   |
| description  | String  | No       | Detailed description of the status          |
| version      | Integer | No       | Optimistic locking version number           |

---

### TradeStatusDTO for POST

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| abbreviation | String  | No       | Short code for the status                   |
| description  | String  | No       | Detailed description of the status          |


---

### ExecutionStatusDTO for GET and PUT

Represents the status of an execution.

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| id           | Integer | No       | Unique identifier for the execution status  |
| abbreviation | String  | No       | Short code for the status                   |
| description  | String  | No       | Detailed description of the status          |
| version      | Integer | No       | Optimistic locking version number           |

---

### ExecutionStatusDTO for POST

| Field        | Type    | Nullable | Description                                 |
|--------------|---------|----------|---------------------------------------------|
| abbreviation | String  | No       | Short code for the status                   |
| description  | String  | No       | Detailed description of the status          |


---

### TradeOrderDTO for GET and PUT

Represents an order placed by a portfolio.

| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| id              | Integer          | No       | Unique identifier for the order                     |
| portfolioId     | String (24 char) | No       | ID of the portfolio making the order                |
| quantity        | Decimal(18,8)    | No       | Amount of security to trade                         |
| tradeTimestamp  | OffsetDateTime   | No       | When the order was placed                           |
| orderId | Integer | No | orderId from Order Service
| version         | Integer          | No       | Optimistic locking version number                   |

---

### TradeOrderDTO for POST

| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| portfolioId     | String (24 char) | No       | ID of the portfolio making the order                |
| quantity        | Decimal(18,8)    | No       | Amount of security to trade                         |
| tradeTimestamp  | OffsetDateTime   | No       | When the order was placed                           |
| orderId | Integer | No | orderId from Order Service


---

### TradeBlockDTO for GET and PUT

Represents a block of trades, which can be allocated to multiple orders.

| Field             | Type                | Nullable | Description                                         |
|-------------------|---------------------|----------|-----------------------------------------------------|
| id                | Integer             | No       | Unique identifier for the trade block               |
| tradeStatus       | TradeStatusDTO      | Yes      | Status of the trade block                           |
| blotter           | BlotterDTO          | No       | The containing blotter                              |
| orderType         | String              | No       | Order type code                                     |
| tradeType         | TradeTypeDTO        | No       | The trade type                                      |
| security          | SecurityDTO         | No       | The security being traded (nested DTO)              |
| quantityOrdered   | Decimal(18,8)       | No       | Amount of security to trade                         |
| limitPrice        | Decimal(18,8)       | No       | Price limit for the trade                           |
| quantityPlaced    | Decimal(18,8)       | No       | Amount placed                                       |
| quantityFilled    | Decimal(18,8)       | No       | Amount filled                                       |
| version           | Integer             | No       | Optimistic locking version number                   |

---

### TradeBlockDTO for POST

| Field             | Type             | Nullable | Description                                         |
|-------------------|------------------|----------|-----------------------------------------------------|
| tradeStatusId     | Integer          | Yes      | ID of the trade status                              |
| blotterId         | Integer          | No       | ID of the containing blotter                        |
| orderType         | String           | No       | Order type code                                     |
| tradeTypeId       | Integer          | No       | ID of the trade type                                |
| securityId        | String (24 char) | No       | ID of the security being traded                     |
| quantityOrdered   | Decimal(18,8)    | No       | Amount of security to trade                         |
| limitPrice        | Decimal(18,8)    | No       | Price limit for the trade                           |
| quantityPlaced    | Decimal(18,8)    | No       | Amount placed                                       |
| quantityFilled    | Decimal(18,8)    | No       | Amount filled                                       |


---

### TradeBlockAllocationDTO for GET and PUT

Represents the allocation of a trade block to a trade order.

| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| id              | Integer          | No       | Unique identifier for the allocation                |
| tradeOrder      | TradeOrderDTO    | No       | The trade order                                    |
| tradeBlock      | TradeBlockDTO    | No       | The trade block                                    |
| version         | Integer          | No       | Optimistic locking version number                   |

---

### TradeBlockAllocationDTO for POST

| Field           | Type    | Nullable | Description                                         |
|-----------------|---------|----------|-----------------------------------------------------|
| tradeOrderId    | Integer | No       | ID of the trade order                               |
| tradeBlockId    | Integer | No       | ID of the trade block                               |


---

### ExecutionDTO for GET and PUT

Represents the execution of a trade block.

| Field                | Type                | Nullable | Description                                         |
|----------------------|---------------------|----------|-----------------------------------------------------|
| id                   | Integer             | No       | Unique identifier for the execution                 |
| tradeBlock           | TradeBlockDTO       | No       | The trade block being executed                      |
| executionStatus      | ExecutionStatusDTO  | Yes      | The execution status                                |
| tradeType            | TradeTypeDTO        | Yes      | The trade type                                      |
| executionTimestamp   | OffsetDateTime      | No       | When the execution occurred                         |
| quantityPlaced       | Decimal(18,8)       | No       | Amount placed                                       |
| quantityFilled       | Decimal(18,8)       | No       | Amount filled                                       |
| limitPrice           | Decimal(18,8)       | Yes      | Price limit for the execution                       |
| version              | Integer             | No       | Optimistic locking version number                   |

---

### ExecutionDTO for POST

| Field                | Type    | Nullable | Description                                         |
|----------------------|---------|----------|-----------------------------------------------------|
| tradeBlockId         | Integer | No       | ID of the trade block being executed                |
| executionStatusId    | Integer | Yes      | ID of the execution status                          |
| tradeTypeId          | Integer | Yes      | ID of the trade type                                |
| executionTimestamp   | OffsetDateTime | No | When the execution occurred                         |
| quantityPlaced       | Decimal(18,8) | No  | Amount placed                                       |
| quantityFilled       | Decimal(18,8) | No  | Amount filled                                       |
| limitPrice           | Decimal(18,8) | Yes | Price limit for the execution                       |

---

### OrderDTO for POST

Represents a trading order in the system.

| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| id              | Integer          | No       | Unique identifier for the order                     |
| portfolioId     | String (24 char) | No       | ID of the portfolio making the order                |
| orderTypeId     | Integer          | No       | Reference to the order type                         |
| securityId      | String (24 char) | No       | ID of the security being traded                     |
| quantity        | Decimal(18,8)    | No       | Amount of security to trade                         |
| limitPrice      | Decimal(18,8)    | Yes      | Price limit for the order (if applicable)           |
| orderTimestamp  | OffsetDateTime   | No       | When the order was placed                           |

---

OrderResponseDTO

Response from placing an order

| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| id              | Integer          | No       | Unique identifier for the order                     |
| portfolioId     | String (24 char) | No       | ID of the portfolio making the order                |
| orderTypeId     | Integer          | No       | Reference to the order type                         |
| securityId      | String (24 char) | No       | ID of the security being traded                     |
| quantity        | Decimal(18,8)    | No       | Amount of security to trade                         |
| limitPrice      | Decimal(18,8)    | Yes      | Price limit for the order (if applicable)           |
| orderTimestamp  | OffsetDateTime   | No       | When the order was placed    




**Notes:**
- All DTOs use types that match the database schema.
- Foreign keys are represented as IDs in request bodies (POST/PUT), and as nested DTOs in response bodies (GET).
- Nullable fields are indicated in the table.
- For lists of resources, return a list of the corresponding DTO objects (e.g., `List<TradeBlockDTO>` for GET `/api/v1/tradeBlocks`).
- For allocations and executions, the nested DTOs provide richer representations for API responses.

---



## REST API Documentation

The following REST APIs are recommended for managing blotters, trade types, trade statuses, execution statuses, trade orders, trade blocks, trade block allocations, and executions in the GlobeCo Trade Service. All endpoints return JSON and use standard HTTP status codes.

---

### Blotter Endpoints

| Method | Path                  | Request Body         | Response Body        | Description                       |
|--------|-----------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/blotters      |                     | [BlotterDTO]         | List all blotters                 |
| GET    | /api/v1/blotter/{id} |                     | BlotterDTO           | Get a blotter by ID               |
| POST   | /api/v1/blotters      | BlotterDTO (POST)   | BlotterDTO           | Create a new blotter              |
| PUT    | /api/v1/blotter/{id} | BlotterDTO          | BlotterDTO           | Update an existing blotter        |
| DELETE | /api/v1/blotter/{id}?version={version} | |                      | Delete a blotter by ID            |

---

### Trade Type Endpoints

| Method | Path                      | Request Body         | Response Body        | Description                       |
|--------|---------------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/tradeTypes        |                     | [TradeTypeDTO]       | List all trade types              |
| GET    | /api/v1/tradeType/{id}   |                     | TradeTypeDTO         | Get a trade type by ID            |
| POST   | /api/v1/tradeTypes        | TradeTypeDTO (POST) | TradeTypeDTO         | Create a new trade type           |
| PUT    | /api/v1/tradeType/{id}   | TradeTypeDTO        | TradeTypeDTO         | Update an existing trade type     |
| DELETE | /api/v1/tradeType/{id}?version={version} | |                      | Delete a trade type by ID         |

---

### Trade Status Endpoints

| Method | Path                      | Request Body         | Response Body        | Description                       |
|--------|---------------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/tradeStatuses     |                     | [TradeStatusDTO]     | List all trade statuses           |
| GET    | /api/v1/tradeStatus/{id}|                     | TradeStatusDTO       | Get a trade status by ID          |
| POST   | /api/v1/tradeStatuses     | TradeStatusDTO (POST)| TradeStatusDTO      | Create a new trade status         |
| PUT    | /api/v1/tradeStatus/{id}| TradeStatusDTO      | TradeStatusDTO       | Update an existing trade status   |
| DELETE | /api/v1/tradeStatus/{id}?version={version} | |                      | Delete a trade status by ID       |

---

### Execution Status Endpoints

| Method | Path                          | Request Body             | Response Body            | Description                       |
|--------|-------------------------------|-------------------------|--------------------------|-----------------------------------|
| GET    | /api/v1/executionStatuses     |                         | [ExecutionStatusDTO]     | List all execution statuses       |
| GET    | /api/v1/executionStatus/{id}|                         | ExecutionStatusDTO       | Get an execution status by ID     |
| POST   | /api/v1/executionStatuses     | ExecutionStatusDTO (POST)| ExecutionStatusDTO      | Create a new execution status     |
| PUT    | /api/v1/executionStatus/{id}| ExecutionStatusDTO      | ExecutionStatusDTO       | Update an existing execution status|
| DELETE | /api/v1/executionStatus/{id}?version={version} | |                          | Delete an execution status by ID  |

---

### Trade Order Endpoints

| Method | Path                      | Request Body         | Response Body        | Description                                 |
|--------|---------------------------|---------------------|----------------------|---------------------------------------------|
| GET    | /api/v1/tradeOrders       |                     | [TradeOrderDTO]      | List all trade orders                       |
| GET    | /api/v1/tradeOrder/{id}  |                     | TradeOrderDTO        | Get a trade order by ID                     |
| POST   | /api/v1/tradeOrders       | TradeOrderDTO (POST)| TradeOrderDTO        | Create a new trade order                    |
| PUT    | /api/v1/tradeOrder/{id}  | TradeOrderDTO       | TradeOrderDTO        | Update an existing trade order              |
| DELETE | /api/v1/tradeOrder/{id}?version={version} | |                      | Delete a trade order by ID                  |

---

### Trade Block Endpoints

| Method | Path                      | Request Body         | Response Body        | Description                                 |
|--------|---------------------------|---------------------|----------------------|---------------------------------------------|
| GET    | /api/v1/tradeBlocks       |                     | [TradeBlockDTO]      | List all trade blocks                       |
| GET    | /api/v1/tradeBlock/{id}  |                     | TradeBlockDTO        | Get a trade block by ID                     |
| POST   | /api/v1/tradeBlocks       | TradeBlockDTO (POST)| TradeBlockDTO        | Create a new trade block                    |
| PUT    | /api/v1/tradeBlock/{id}  | TradeBlockDTO       | TradeBlockDTO        | Update an existing trade block              |
| DELETE | /api/v1/tradeBlock/{id}?version={version} | |                      | Delete a trade block by ID                  |

---

### Trade Block Allocation Endpoints

| Method | Path                              | Request Body                 | Response Body                | Description                                 |
|--------|-----------------------------------|-----------------------------|------------------------------|---------------------------------------------|
| GET    | /api/v1/tradeBlockAllocations     |                             | [TradeBlockAllocationDTO]     | List all trade block allocations            |
| GET    | /api/v1/tradeBlockAllocation/{id}|                             | TradeBlockAllocationDTO       | Get a trade block allocation by ID          |
| POST   | /api/v1/tradeBlockAllocations     | TradeBlockAllocationDTO (POST)| TradeBlockAllocationDTO     | Create a new trade block allocation         |
| PUT    | /api/v1/tradeBlockAllocation/{id}| TradeBlockAllocationDTO     | TradeBlockAllocationDTO       | Update an existing trade block allocation   |
| DELETE | /api/v1/tradeBlockAllocation/{id}?version={version} | |                  | Delete a trade block allocation by ID       |

---

### Execution Endpoints

| Method | Path                      | Request Body         | Response Body        | Description                                 |
|--------|---------------------------|---------------------|----------------------|---------------------------------------------|
| GET    | /api/v1/executions        |                     | [ExecutionDTO]       | List all executions                         |
| GET    | /api/v1/execution/{id}   |                     | ExecutionDTO         | Get an execution by ID                      |
| POST   | /api/v1/executions        | ExecutionDTO (POST) | ExecutionDTO         | Create a new execution                      |
| PUT    | /api/v1/execution/{id}   | ExecutionDTO        | ExecutionDTO         | Update an existing execution                |
| DELETE | /api/v1/execution/{id}?version={version} | |                      | Delete an execution by ID                   |

---

#### Notes
- All POST and PUT endpoints expect the corresponding DTO in the request body.
- All GET endpoints return the DTO or a list of DTOs as described above.
- Response DTOs use nested objects for related entities (e.g., `tradeType`, `blotter`, etc.).
- Request DTOs use IDs for foreign keys.
- Standard error responses (e.g., 404 Not Found, 400 Bad Request, 409 Conflict) should be used as appropriate.

---

**Example: Create Trade Block Request (POST /api/v1/tradeBlocks)**
```json
{
  "tradeStatusId": 2,
  "blotterId": 1,
  "orderType": "LMT",
  "tradeTypeId": 3,
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantityOrdered": 100.00000000,
  "limitPrice": 50.25000000,
  "quantityPlaced": 100.00000000,
  "quantityFilled": 0.00000000,
  "version": 1
}
```

**Example: Get Trade Block Response (GET /api/v1/tradeBlocks/42)**
```json
{
  "id": 42,
  "tradeStatus": {
    "id": 2,
    "abbreviation": "NEW",
    "description": "New Block",
    "version": 1
  },
  "blotter": {
    "id": 1,
    "abbreviation": "EQ",
    "name": "Equities",
    "version": 1
  },
  "orderType": "LMT",
  "tradeType": {
    "id": 3,
    "abbreviation": "BUY",
    "description": "Buy",
    "version": 1
  },
  "security": {
    "securityId": "5f47ac10b8e4e53b8cfa9b1b",
    "ticker": "AAPL",
    "description": "Apple Inc.",
    "securityType": {
      "securityTypeId": "equity",
      "abbreviation": "EQ",
      "description": "Equity"
    },
    "version": 1
  },
  "quantityOrdered": 100.00000000,
  "limitPrice": 50.25000000,
  "quantityPlaced": 100.00000000,
  "quantityFilled": 0.00000000,
  "version": 1
}
```

**Example: Create Execution Request (POST /api/v1/executions)**
```json
{
  "tradeBlockId": 42,
  "executionStatusId": 1,
  "tradeTypeId": 3,
  "executionTimestamp": "2024-06-01T12:00:00Z",
  "quantityPlaced": 100.00000000,
  "quantityFilled": 100.00000000,
  "limitPrice": 50.25000000,
  "version": 1
}
```

**Example: Get Execution Response (GET /api/v1/executions/99)**
```json
{
  "id": 99,
  "tradeBlock": {
    "id": 42,
    "tradeStatus": {
      "id": 2,
      "abbreviation": "NEW",
      "description": "New Block",
      "version": 1
    },
    "blotter": {
      "id": 1,
      "abbreviation": "EQ",
      "name": "Equities",
      "version": 1
    },
    "orderType": "LMT",
    "tradeType": {
      "id": 3,
      "abbreviation": "BUY",
      "description": "Buy",
      "version": 1
    },
    "security": {
      "securityId": "5f47ac10b8e4e53b8cfa9b1b",
      "ticker": "AAPL",
      "description": "Apple Inc.",
      "securityType": {
        "securityTypeId": "equity",
        "abbreviation": "EQ",
        "description": "Equity"
      },
      "version": 1
    },
    "quantityOrdered": 100.00000000,
    "limitPrice": 50.25000000,
    "quantityPlaced": 100.00000000,
    "quantityFilled": 0.00000000,
    "version": 1
  },
  "executionStatus": {
    "id": 1,
    "abbreviation": "FILLED",
    "description": "Filled",
    "version": 1
  },
  "tradeType": {
    "id": 3,
    "abbreviation": "BUY",
    "description": "Buy",
    "version": 1
  },
  "executionTimestamp": "2024-06-01T12:00:00Z",
  "quantityPlaced": 100.00000000,
  "quantityFilled": 100.00000000,
  "limitPrice": 50.25000000,
  "version": 1
}
```

---



