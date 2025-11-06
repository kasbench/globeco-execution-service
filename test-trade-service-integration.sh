#!/bin/bash

# Script to test the trade service integration in Kubernetes
# This script will:
# 1. Create an execution with a trade service execution ID
# 2. Update the execution with fill information
# 3. Check if the updateExecutionFill method is called

set -e

# Configuration
EXECUTION_SERVICE_URL="http://localhost:8084"  # Adjust if using port-forward
TRADE_SERVICE_EXECUTION_ID=12345

echo "=== Testing Trade Service Integration ==="
echo "Execution Service URL: $EXECUTION_SERVICE_URL"
echo "Trade Service Execution ID: $TRADE_SERVICE_EXECUTION_ID"
echo

# Step 1: Create an execution with trade service execution ID
echo "Step 1: Creating execution with trade service execution ID..."
CREATE_RESPONSE=$(curl -s -X POST "$EXECUTION_SERVICE_URL/api/v1/execution" \
  -H "Content-Type: application/json" \
  -d '{
    "executionStatus": "NEW",
    "side": "BUY",
    "exchange": "NYSE",
    "securityId": "SEC123456789012345678901",
    "quantity": 100.00,
    "price": 50.00,
    "tradeServiceExecutionId": '$TRADE_SERVICE_EXECUTION_ID'
  }')

echo "Create response: $CREATE_RESPONSE"

# Extract execution ID and version from response
EXECUTION_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')
VERSION=$(echo "$CREATE_RESPONSE" | jq -r '.version')

if [ "$EXECUTION_ID" = "null" ] || [ "$VERSION" = "null" ]; then
    echo "ERROR: Failed to create execution or extract ID/version"
    echo "Response: $CREATE_RESPONSE"
    exit 1
fi

echo "Created execution with ID: $EXECUTION_ID, Version: $VERSION"
echo

# Step 2: Update the execution with fill information
echo "Step 2: Updating execution with fill information..."
UPDATE_RESPONSE=$(curl -s -X PUT "$EXECUTION_SERVICE_URL/api/v1/execution/$EXECUTION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "quantityFilled": 25.00,
    "averagePrice": 50.50,
    "version": '$VERSION'
  }')

echo "Update response: $UPDATE_RESPONSE"
echo

# Step 3: Check the logs for trade service calls
echo "Step 3: Check the execution service logs for trade service integration messages:"
echo "Look for these log messages:"
echo "  - 'TradeServiceClient.updateExecutionFill called for execution'"
echo "  - 'Updating execution fill in trade service'"
echo "  - 'Successfully updated trade service for execution' or 'Failed to update trade service'"
echo
echo "If running in Kubernetes, use:"
echo "  kubectl logs -f deployment/globeco-execution-service | grep -i trade"
echo
echo "If using docker-compose, use:"
echo "  docker logs globeco-execution-service | grep -i trade"
echo

echo "=== Test completed ==="
echo "Execution ID: $EXECUTION_ID"
echo "Check the logs to see if updateExecutionFill was called!"