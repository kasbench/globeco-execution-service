# Step-by-Step Instructions

Please perform each step when instructed.  Only perform one step at a time.

Within these instructions, use the following table to map between table names, resource names, and class names.

| table name | resource name | class name |
| --- | --- | --- |
| execution | execution | Execution |
---

Log each step in @cursor-log.md.  

## Steps

1. Configure the project to connect to the PostgreSQL database on host `globeco-execution-service-postgresql`  port 5436 and database `postgres`.  The user is  "postgres".  
2. Configure Flyway with the same configuration as in step 1.  
3. Configure the project to use PostgreSQL and Kafka test containers
4. Configure a Kafka producer using the requirements in the "## Kafka" section of @requirements.md.  The program should create the topic if it doesn't already exist.
5. Create a Flyway migration to deploy the schema for this project.  The schema is in @execution-service.sql in the project root.  
6. Please implement the entity, repository, service interface, and service implementation for **execution** using the requirements provided in @requirements.md.  
7. Please implement the unit tests for the entity, repository, service interface, and service implementation for **execution**.  Please use test containers.  
8. Please impelement caching for **execution** using the requirements in @requirements.md. 
9. Please implement unit testing for **execution** caching.  
10. Implement the DTOs in the "## Data Transfer Objects (DTOs)" section of @requirements.md.
11. Implement the APIs in the "## REST API Documentation" section of @requirements.md.
12. Implement tests for the APIs generated in the previous step.
13. Modify the POST API and corresponding test created in the previous steps.  Perform the following steps in a transaction.
    1. Save the API data to the execution table.  `received_timestamp` should be set to the current time.  `sent_timestamp` is null.
    2. Populate the ExecutionDTO.  Set the sentTimestamp to the current time.
    3. Send the ExecutionDTO to Kafka on the orders topic.
    4. Update the database with the sent_timestamp from step 2.
14. We will be deploying this service to Kubernetes.  Please implement liveness, readiness, and startup health checks.  
15. Please document the service completely in README.md.
16. Please create a Dockerfile for this application.  
17. Please create all the files necessary to deploy to this application as a service to Kubernetes.  Please include the liveness, readiness, and startup probes you just created.  The deployment should start with one instance of the service and should scale up to a maximum of 100 instances.  It should have up 100 millicores and 200 MiB of memory.  The liveness probe should have a timeout (`timeoutSeconds`) of 240 seconds.  The name of the service is `globeco-execution-service` in the `globeco` namespace.  You do not need to create the namespace.  Please put files in the k8s directory.
18. Please expose the OpenAPI schema as an endpoint using Springdoc OpenAPI. 
19. Please add the URLS for the OpenAPI schema to the README.md file

