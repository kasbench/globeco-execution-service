# GlobeCo Execution Service Requirements

## Background

This document provides requirements for the Execution Service.  This service is designed as a bridge between the trading service and the fix engine.  It receives trades synchronously from the trading service and sends them to the FIX engine as Kafka events.

This microservice will be deployed on Kubernetes 1.33.

This microservice is part of the GlobeCo suite of applications for benchmarking Kubernetes autoscaling.

Name of service: Execution Service <br>
Host: globeco-execution-service <br>
Port: 8084 <br>

Author: Noah Krieger <br>
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
| PostgreSQL (Database) | 17 | Relational database |
| Caffeine | 3.1.8 | In-memory caching provider for Spring's caching abstraction (5 minute TTL) |
| Spring Kafka | (from BOM) | Kafka support
---

Notes:
- (from BOM) means the version is managed by the Spring Boot BOM (Bill of Materials) and will match the Spring Boot version unless overridden.
- All dependencies are managed via Maven Central.
- The project uses Gradle as the build tool.
- Spring's caching abstraction is used with Caffeine for in-memory caching with a 5 minute time-to-live (TTL) for relevant caches.



## Other services

| Name | Host | Port | Description |
| --- | --- | --- | --- |
Kafka | globeco-execution-service-kafka-kafka-1 | 9092 | Kafka cluster |
---

## Kafka
- Bootstrap Server: globeco-execution-service-kafka-kafka-1 
- Port: 9092 <br>
- Topic: orders





## Caching
- Use Spring's caching abstraction for `execution`
- Caches should have a 5 minute TTL



## Database Information

The database is at globeco-execution-service-postgresql:5436
The database is the default `postgres` database.
The schema is the default `public` schema.
The owner of all database objects is `postgres`.


## Entity Relationship Diagram

<img src="./images/execution-service.png">


## Data Dictionary


### _public_.**execution** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| execution_status | varchar(20) |  |  |  | &#10003; |  |  |
| trade_type | varchar(10) |  |  |  | &#10003; |  |  |
| destination | varchar(20) |  |  |  | &#10003; |  |  |
| quantity | decimal(18,8) |  |  |  | &#10003; |  |  |
| limit_price | decimal(18,8) |  |  |  |  |  |  |
| received_timestamp | timestamptz |  |  |  | &#10003; |  |  |
| sent_timestamp | timestamptz |  |  |  |  |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| execution_pk | PRIMARY KEY | id |  |  |  |  |  |

---


## Data Transfer Objects (DTOs)

The following DTOs represent the data structures used to transfer information between the API and clients for the main entities in the GlobeCo Trade Service.


### ExecutionPostDTO

Represents an execution.

| Field           | Type    | Nullable | Description                        |
|-----------------|---------|----------|------------------------------------|
| executionStatus | String | No |
| tradeType | String | No |
| destination | String | No |
| quantity | BigDecimal | No |
| limitPrice | BigDecimal | Yes |
| version         | Integer | No       | Optimistic locking version number  |

### ExecutionDTO

Represents an execution.

| Field           | Type    | Nullable | Description                        |
|-----------------|---------|----------|------------------------------------|
| id | Integer | No |
| executionStatus | String | No |
| tradeType | String | No |
| destination | String | No |
| quantity | BigDecimal | No |
| limitPrice | BigDecimal | Yes |
| receivedTimestamp | OffsetDateTime  | No |
| sentTimestamp | OffsetDateTime | No |
| version         | Integer | No       | Optimistic locking version number  |





---

### Column to API Field Mapping

Table execution maps to resource Execution.

| Database Column | Resource Field |
| --- | --- |
| id | id |
| execution_status | executionStatus |
| trade_type | tradeType |
| destination | destination |
| quantity | quantity |
| limit_price | limitPrice |
| received_timestamp | receivedTimestamp |
| sent_timestamp | sentTimestamp |
| version | version |
---



## REST API Documentation



| Method | Path                  | Request Body         | Response Body        | Description                       |
|--------|-----------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/execution      |                     | [BlotterDTO]         | List all blotters                 |
| GET    | /api/v1/blotter/{id} |                     | BlotterDTO           | Get a blotter by ID               |
| POST   | /api/v1/blotters      | BlotterDTO (POST)   | BlotterDTO           | Create a new blotter              |
| PUT    | /api/v1/blotter/{id} | BlotterDTO          | BlotterDTO           | Update an existing blotter        |
| DELETE | /api/v1/blotter/{id}?version={version} | |                      | Delete a blotter by ID            |

