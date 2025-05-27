# Supplemental Requirement 1

This document describes a supplemental requirement to enhance the `execution` functionality by adding two new columns to the `execution` table and implementing a new PUT API endpoint to update those columns. The changes include database migration, DTO updates, service logic modifications, and corresponding tests.

## Requirements

### 1. New Columns in `execution` Table

Add the following columns to the `execution` table:

| Column Name      | Type            | Nullable | Default Value |
|------------------|-----------------|----------|---------------|
| quantity_filled  | decimal(18,8)   | Yes      | 0             |
| average_price    | decimal(18,8)   | Yes      | null          |

### 2. New `ExecutionPutDTO`

Create a new DTO for the PUT request with the following fields:

| Field          | Type        | Nullable | Description                                         |
|----------------|-------------|----------|-----------------------------------------------------|
| quantityFilled | BigDecimal  | No       | Amount to increment `quantity_filled` in the database|
| averagePrice   | BigDecimal  | Yes      | Value to set as `average_price` in the database      |
| version        | Integer     | No       | Optimistic locking version number                    |

### 3. Modify `ExecutionDTO`

- Add `quantityFilled` and `executionStatus` fields to the existing `ExecutionDTO` schema, immediately before the `version` field.

### 4. New API Endpoint

Implement the following endpoint:

| Method | Path                    | Request Body      | Response Body   | Description                |
|--------|-------------------------|-------------------|-----------------|----------------------------|
| PUT    | /api/v1/execution/{id}  | ExecutionPutDTO   | ExecutionDTO    | Update an Execution record |

### 5. Logic Changes

- When creating a new execution (POST), set `quantity_filled` to 0 and `average_price` to null by default.
- For the PUT endpoint:
    - Increment `quantity_filled` in the database by the value of `quantityFilled` from the request.
    - Set `average_price` in the database to the value of `averagePrice` from the request.
    - Use optimistic concurrency control based on the `version` field.
    - After updating, if `quantity_filled` is less than `quantity`, set `execution_status` to 'PART'. Otherwise, set it to 'FULL'.
    - (Note: In production, additional error handling would be required.)

## Steps

1. **Implement Database Migration**
    - Create a migration script to add `quantity_filled` and `average_price` columns to the `execution` table with the specified types and default values.
    - Apply the migration and verify the schema changes.

2. **Update DTOs**
    - Create `ExecutionPutDTO` with the specified fields.
    - Update `ExecutionDTO` to include `quantityFilled` and `executionStatus` fields in the correct order.

3. **Modify POST Logic and Tests**
    - Update the logic for creating a new execution to set `quantity_filled` to 0 and `average_price` to null by default.
    - Update or add tests to verify these default values are set correctly.

4. **Implement PUT Logic and Tests**
    - Implement the PUT endpoint at `/api/v1/execution/{id}` to update the specified fields according to the requirements.
    - Ensure optimistic concurrency is enforced using the `version` field.
    - Update `execution_status` based on the new value of `quantity_filled`.
    - Add or update tests to cover the new logic, including concurrency and status updates.

5. **Update Documentation**
    - Update `README.md` and any relevant API documentation to describe the new fields, endpoint, and logic.
    - Document example requests and responses for the new PUT endpoint.

6. **Review and Refactor**
    - Review code for consistency and adherence to project conventions.
    - Refactor as needed for clarity and maintainability.
    - Ensure all tests pass and code coverage is adequate.

