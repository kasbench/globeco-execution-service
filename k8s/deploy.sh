#!/bin/bash
set -e

NAMESPACE=globeco
POSTGRES_STS=globeco-execution-service-postgresql
KAFKA_STS=globeco-execution-service-kafka

# Deploy PostgreSQL StatefulSet and Service
kubectl apply -f k8s/postgresql-deployment.yaml

echo "Waiting for PostgreSQL StatefulSet to be ready..."
# Wait for the StatefulSet to have at least 1 ready replica
until kubectl -n "$NAMESPACE" get statefulset "$POSTGRES_STS" -o jsonpath='{.status.readyReplicas}' | grep -q '^1$'; do
  echo "  ...still waiting for PostgreSQL to be ready..."
  sleep 5
done

echo "PostgreSQL StatefulSet is ready. Deploying Kafka."

# Deploy Kafka StatefulSet and Service
kubectl apply -f k8s/kafka.yaml

echo "Waiting for Kafka StatefulSet to be ready..."
# Wait for the Kafka StatefulSet to have at least 1 ready replica
until kubectl -n "$NAMESPACE" get statefulset "$KAFKA_STS" -o jsonpath='{.status.readyReplicas}' | grep -q '^1$'; do
  echo "  ...still waiting for Kafka to be ready..."
  sleep 5
done

echo "Kafka StatefulSet is ready. Deploying application."

# Deploy application Deployment and Service
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

echo "Deployment complete."
