docker buildx build --platform linux/amd64,linux/arm64 \
-t kasbench/globeco-execution-service:latest \
-t kasbench/globeco-execution-service:1.0.1 \
--push .
kubectl delete -f k8s/deployment.yaml
kubectl apply -f k8s/deployment.yaml
