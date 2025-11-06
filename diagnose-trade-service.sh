#!/bin/bash

# Diagnostic script to check trade service integration
set -e

EXECUTION_SERVICE_URL="http://localhost:8084"

echo "=== Trade Service Integration Diagnostics ==="
echo

# Check if execution service is running
echo "1. Checking if execution service is accessible..."
if curl -s "$EXECUTION_SERVICE_URL/actuator/health" > /dev/null; then
    echo "✓ Execution service is accessible"
    HEALTH=$(curl -s "$EXECUTION_SERVICE_URL/actuator/health" | jq -r '.status')
    echo "  Health status: $HEALTH"
else
    echo "✗ Execution service is not accessible at $EXECUTION_SERVICE_URL"
    echo "  Make sure to port-forward if running in Kubernetes:"
    echo "  kubectl port-forward deployment/globeco-execution-service 8084:8084"
    exit 1
fi
echo

# Check existing executions
echo "2. Checking existing executions..."
EXECUTIONS=$(curl -s "$EXECUTION_SERVICE_URL/api/v1/executions?limit=5")
EXECUTION_COUNT=$(echo "$EXECUTIONS" | jq -r '.totalElements // 0')
echo "  Total executions: $EXECUTION_COUNT"

if [ "$EXECUTION_COUNT" -gt 0 ]; then
    echo "  Sample executions with trade service IDs:"
    echo "$EXECUTIONS" | jq -r '.content[] | select(.tradeServiceExecutionId != null) | "    ID: \(.id), TradeServiceID: \(.tradeServiceExecutionId), Status: \(.executionStatus), Filled: \(.quantityFilled)"'
fi
echo

# Check application configuration
echo "3. Checking trade service configuration..."
echo "  You can verify the configuration by checking the logs for:"
echo "  - Trade service base URL"
echo "  - Retry settings"
echo "  - Connection timeouts"
echo

echo "4. To monitor trade service calls in real-time:"
echo "  kubectl logs -f deployment/globeco-execution-service | grep -E '(TradeServiceClient|trade service)'"
echo

echo "5. To test the integration:"
echo "  ./test-trade-service-integration.sh"
echo

echo "=== Diagnostics completed ==="