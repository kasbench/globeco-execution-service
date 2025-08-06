# Docker Build Guide - Globeco Execution Service

This guide covers building and deploying the optimized multi-architecture Docker image for the Globeco Execution Service.

## 🚀 Quick Start

### Local Development
```bash
# Start all services (database, kafka, mocks, app)
docker-compose up -d

# View logs
docker-compose logs -f execution-service

# Stop all services
docker-compose down
```

### Multi-Architecture Build
```bash
# Build for local use (current architecture only)
./docker-build.sh

# Build with custom tag
./docker-build.sh --tag v1.2.3

# Build and push to registry
./docker-build.sh --tag v1.2.3 --registry your-registry.com/your-org
```

## 🏗️ Build Optimizations

### Layer Caching Strategy
The Dockerfile uses a multi-stage approach to optimize build times:

1. **Dependencies Stage**: Downloads and caches Gradle dependencies
2. **Build Stage**: Compiles source code using cached dependencies
3. **Runtime Stage**: Creates minimal runtime image

### Key Optimizations:
- **Dependency Caching**: Dependencies are downloaded in a separate stage and cached
- **Build Cache**: Gradle build cache is enabled for faster incremental builds
- **Multi-Architecture**: Supports both ARM64 and AMD64 architectures
- **Minimal Runtime**: Uses JRE instead of JDK for smaller image size
- **Security**: Runs as non-root user

## 📦 Build Stages Explained

### Stage 1: Dependencies (`deps`)
```dockerfile
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS deps
# Downloads all Gradle dependencies
# This layer is cached unless build.gradle changes
```

### Stage 2: Build (`build`)
```dockerfile
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
# Copies cached dependencies and builds the application
# Only rebuilds if source code changes
```

### Stage 3: Runtime
```dockerfile
FROM eclipse-temurin:21-jre-jammy
# Minimal runtime image with security hardening
```

## 🌐 Multi-Architecture Support

### Supported Platforms:
- `linux/amd64` (Intel/AMD 64-bit)
- `linux/arm64` (ARM 64-bit, including Apple Silicon)

### Build Commands:

#### Local Build (Single Architecture)
```bash
# Builds for your current platform only
docker build -t globeco-execution-service:latest .
```

#### Multi-Architecture Build
```bash
# Using the provided script (recommended)
./docker-build.sh --tag latest

# Manual buildx command
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag globeco-execution-service:latest \
  --push .
```

## 🔧 Build Script Options

The `docker-build.sh` script supports various options:

```bash
# Basic usage
./docker-build.sh

# With custom tag
./docker-build.sh --tag v1.2.3

# Push to registry
./docker-build.sh --tag v1.2.3 --registry your-registry.com

# Specific platforms
./docker-build.sh --platforms linux/amd64

# Help
./docker-build.sh --help
```

### Environment Variables:
- `TAG`: Default image tag (default: `latest`)
- `REGISTRY`: Registry URL for pushing images

## 🐳 Docker Compose Development

### Services Included:
- **PostgreSQL**: Database with schema initialization
- **Kafka**: Message broker (using Redpanda)
- **Mock Services**: WireMock-based mocks for external dependencies
- **Execution Service**: The main application

### Usage:
```bash
# Start all services
docker-compose up -d

# Start specific service
docker-compose up -d postgres

# View logs
docker-compose logs -f execution-service

# Rebuild and restart service
docker-compose up -d --build execution-service

# Clean up
docker-compose down -v  # -v removes volumes
```

## 🚀 Performance Features

### Container Optimizations:
- **Memory Management**: Uses container-aware JVM settings
- **Garbage Collection**: Optimized G1GC configuration
- **String Deduplication**: Reduces memory usage
- **Fast Startup**: Optimized for container environments

### JVM Options:
```bash
-XX:+UseContainerSupport      # Container-aware memory limits
-XX:MaxRAMPercentage=75.0     # Use 75% of available memory
-XX:+UseG1GC                  # G1 garbage collector
-XX:G1HeapRegionSize=16m      # Optimized heap regions
-XX:+UseStringDeduplication   # Reduce string memory usage
```

## 📊 Build Performance Tips

### Faster Builds:
1. **Use Build Cache**: Enable Docker BuildKit for better caching
2. **Minimize Context**: Use `.dockerignore` to exclude unnecessary files
3. **Layer Ordering**: Dependencies change less frequently than source code
4. **Parallel Builds**: Use `--parallel` flag with docker-compose

### BuildKit Configuration:
```bash
# Enable BuildKit
export DOCKER_BUILDKIT=1

# Or use buildx (recommended)
docker buildx build --cache-from type=local,src=/tmp/.buildx-cache
```

## 🔒 Security Features

### Runtime Security:
- **Non-root User**: Application runs as `appuser`
- **Minimal Base Image**: Uses distroless-style JRE image
- **Health Checks**: Built-in health monitoring
- **Resource Limits**: Configured memory and CPU limits

### Security Scanning:
```bash
# Scan for vulnerabilities
docker scout cves globeco-execution-service:latest

# Or use trivy
trivy image globeco-execution-service:latest
```

## 🚀 Deployment Examples

### Kubernetes Deployment:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: globeco-execution-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: globeco-execution-service
  template:
    metadata:
      labels:
        app: globeco-execution-service
    spec:
      containers:
      - name: execution-service
        image: your-registry.com/globeco-execution-service:v1.2.3
        ports:
        - containerPort: 8084
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8084
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8084
          initialDelaySeconds: 30
          periodSeconds: 10
```

### Docker Swarm:
```yaml
version: '3.8'
services:
  execution-service:
    image: your-registry.com/globeco-execution-service:v1.2.3
    deploy:
      replicas: 3
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
        reservations:
          memory: 512M
          cpus: '0.25'
    ports:
      - "8084:8084"
```

## 🔍 Troubleshooting

### Common Issues:

#### Build Fails with "buildx not found"
```bash
# Install buildx plugin
docker buildx install

# Or use Docker Desktop which includes buildx
```

#### Multi-arch build fails
```bash
# Ensure QEMU is installed for cross-platform builds
docker run --privileged --rm tonistiigi/binfmt --install all

# Create and use a new builder
docker buildx create --name multiarch --use
docker buildx inspect --bootstrap
```

#### Out of disk space during build
```bash
# Clean up Docker system
docker system prune -a

# Remove unused build cache
docker buildx prune
```

### Performance Issues:
- Check container resource limits
- Monitor JVM memory usage with `/actuator/metrics`
- Use performance testing endpoint: `/api/v1/performance-test/executions-benchmark`

## 📈 Monitoring

### Health Checks:
- **Liveness**: `/actuator/health`
- **Readiness**: `/actuator/health/readiness`
- **Metrics**: `/actuator/metrics`

### Performance Monitoring:
- **Cache Stats**: `/api/v1/monitoring/security-cache-stats`
- **Performance Test**: `/api/v1/performance-test/executions-benchmark`

## 🎯 Best Practices

1. **Tag Images**: Always use specific tags, avoid `latest` in production
2. **Multi-Stage Builds**: Keep runtime images minimal
3. **Security Scanning**: Regularly scan images for vulnerabilities
4. **Resource Limits**: Set appropriate CPU and memory limits
5. **Health Checks**: Implement comprehensive health monitoring
6. **Logging**: Use structured logging for better observability

## 📚 Additional Resources

- [Docker Multi-Architecture Builds](https://docs.docker.com/buildx/working-with-buildx/)
- [Spring Boot Docker Guide](https://spring.io/guides/topicals/spring-boot-docker/)
- [Container Security Best Practices](https://docs.docker.com/develop/security-best-practices/)