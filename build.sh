#!/bin/bash

# Comprehensive build script for globeco-execution-service
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

# Configuration
IMAGE_NAME="kasbench/globeco-execution-service"
TAG="${TAG:-latest}"
BUILD_JAVA="${BUILD_JAVA:-true}"
BUILD_DOCKER="${BUILD_DOCKER:-true}"
PUSH_IMAGE="${PUSH_IMAGE:-true}"

log "Starting build process for globeco-execution-service"
log "Configuration:"
log "  Image: $IMAGE_NAME:$TAG"
log "  Build Java: $BUILD_JAVA"
log "  Build Docker: $BUILD_DOCKER"
log "  Push Image: $PUSH_IMAGE"

# Build Java application
if [[ "$BUILD_JAVA" == "true" ]]; then
    log "Building Java application..."
    ./gradlew clean build
    success "Java build completed successfully!"
else
    warn "Skipping Java build (BUILD_JAVA=false)"
fi

# Build Docker image
if [[ "$BUILD_DOCKER" == "true" ]]; then
    if command -v docker &> /dev/null; then
        log "Building optimized multi-architecture Docker image..."
        
        # Check if buildx is available for multi-arch builds
        if docker buildx version &> /dev/null; then
            log "Using Docker buildx for multi-architecture build..."
            
            # Build arguments
            build_args=(
                --platform "linux/amd64,linux/arm64"
                --tag "$IMAGE_NAME:$TAG"
                --file Dockerfile
                .
            )
            
            # Add push flag if enabled
            if [[ "$PUSH_IMAGE" == "true" ]]; then
                build_args+=(--push)
                log "Will push to registry after build"
            else
                build_args+=(--load)
                warn "Will build for local use only (PUSH_IMAGE=false)"
                # For local load, we can only build for current platform
                build_args[1]="linux/$(docker version --format '{{.Server.Arch}}')"
            fi
            
            # Execute build
            docker buildx build "${build_args[@]}"
            
            if [[ "$PUSH_IMAGE" == "true" ]]; then
                success "Multi-architecture Docker image built and pushed: $IMAGE_NAME:$TAG"
            else
                success "Docker image built locally: $IMAGE_NAME:$TAG"
            fi
        else
            warn "Buildx not available, using standard Docker build..."
            docker build -t "$IMAGE_NAME:$TAG" .
            
            if [[ "$PUSH_IMAGE" == "true" ]]; then
                log "Pushing image to registry..."
                docker push "$IMAGE_NAME:$TAG"
                success "Docker image pushed: $IMAGE_NAME:$TAG"
            else
                success "Docker image built locally: $IMAGE_NAME:$TAG"
            fi
        fi
    else
        error "Docker not found. Cannot build Docker image."
        exit 1
    fi
else
    warn "Skipping Docker build (BUILD_DOCKER=false)"
fi

success "Build process completed successfully!"

# Show final status
log "Build Summary:"
if [[ "$BUILD_JAVA" == "true" ]]; then
    log "  ✅ Java application built"
fi
if [[ "$BUILD_DOCKER" == "true" ]]; then
    if [[ "$PUSH_IMAGE" == "true" ]]; then
        log "  ✅ Docker image built and pushed: $IMAGE_NAME:$TAG"
    else
        log "  ✅ Docker image built locally: $IMAGE_NAME:$TAG"
    fi
fi

log "You can now:"
log "  • Test locally: docker-compose up -d"
log "  • Deploy to Kubernetes: kubectl apply -f k8s/"
log "  • Run performance tests: curl http://localhost:8084/api/v1/performance-test/executions-benchmark"
