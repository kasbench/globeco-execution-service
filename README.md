# globeco-execution-service

The Execution Service in the GlobeCo suite for benchmarking Kubernetes autoscaling.

## Overview

The Execution Service acts as a bridge between the trading service and the FIX engine. It receives trades synchronously from the trading service and sends them to the FIX engine as Kafka events. The service is designed for high scalability and observability, and is deployed on Kubernetes.

## Features
- RESTful API for managing executions
- PostgreSQL for persistent storage
- Kafka integration for event streaming
- Caching with Caffeine (5 minute TTL)
- Database migrations with Flyway
- Health checks for Kubernetes (liveness, readiness, startup)
- Testcontainers-based integration tests
- OpenAPI documentation

## Technology Stack
- Java 21
- Spring Boot 3.4.5
- PostgreSQL 17
- Kafka
- Caffeine (caching)
- Flyway (migrations)
- Gradle (build tool)
- Testcontainers (testing)

## Configuration

Configuration is managed via `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://globeco-execution-service-postgresql:5436/postgres
spring.datasource.username=postgres
spring.datasource.password=
spring.kafka.bootstrap-servers=globeco-execution-service-kafka-kafka-1:9092
kafka.topic.orders=orders
```

## Database
- Schema managed by Flyway (`src/main/resources/db/migration/V1__init.sql`)
- Main table: `execution`

### Entity Relationship Diagram

![ERD](documentation/images/execution-service.png)

## Kafka
- Bootstrap server: `globeco-execution-service-kafka-kafka-1:9092`
- Topic: `orders`

## REST API

| Method | Path                  | Request Body         | Response Body        | Description                       |
|--------|-----------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/executions      |                     | [ExecutionDTO]         | List all executions                 |
| GET    | /api/v1/execution/{id} |                     | ExecutionDTO           | Get an execution by ID               |
| POST   | /api/v1/executions      | ExecutionPostDTO   | ExecutionDTO           | Create a new execution              |

### Data Transfer Objects
- `ExecutionPostDTO`: Used for creating/updating executions
- `ExecutionDTO`: Used for returning execution data

## Health Checks
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Startup: `/actuator/health/startup`

## Testing
- Unit and integration tests use Testcontainers for PostgreSQL and Kafka
- Tests are located in `src/test/java/org/kasbench/globeco_execution_service/`

## Running Locally

1. Start PostgreSQL and Kafka (see configuration above)
2. Run the application:
   ```sh
   ./gradlew bootRun
   ```
3. Access the API at `http://localhost:8084/api/v1/execution`

## Database Migration
- Flyway will automatically apply migrations on startup

## Deployment
- Designed for Kubernetes deployment
- Health checks are enabled for liveness, readiness, and startup


## OpenAPI & Documentation
- OpenAPI schema: [http://localhost:8084/v3/api-docs](http://localhost:8084/v3/api-docs)
- Swagger UI: [http://localhost:8084/swagger-ui.html](http://localhost:8084/swagger-ui.html)

## Author
Noah Krieger (<noah@kasbench.org>)


---
For more details, see the requirements in `documentation/requirements.md`.
