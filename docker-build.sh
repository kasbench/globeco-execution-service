#!/bin/bash

# Multi-architecture Docker build script for globeco-execution-service
# Supports ARM64 and AMD64 architectures with build optimization

set -e

# Configuration
IMAGE_NAME="globeco-execution-service"
TAG="${TAG:-latest}"
REGISTRY="${REGISTRY:-}"
PLATFORMS="linux/amd64,linux/arm64"

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

# Function to check if buildx is available
check_buildx() {
    if ! docker buildx version >/dev/null 2>&1; then
        error "Docker buildx is not available. Please install Docker Desktop or enable buildx."
        exit 1
    fi
}

# Function to create/use buildx builder
setup_builder() {
    local builder_name="multiarch-builder"
    
    log "Setting up multi-architecture builder..."
    
    # Check if builder exists
    if ! docker buildx inspect "$builder_name" >/dev/null 2>&1; then
        log "Creating new buildx builder: $builder_name"
        docker buildx create --name "$builder_name" --driver docker-container --use
    else
        log "Using existing buildx builder: $builder_name"
        docker buildx use "$builder_name"
    fi
    
    # Bootstrap the builder
    log "Bootstrapping builder..."
    docker buildx inspect --bootstrap
}

# Function to build and optionally push the image
build_image() {
    local full_image_name="$IMAGE_NAME:$TAG"
    if [[ -n "$REGISTRY" ]]; then
        full_image_name="$REGISTRY/$full_image_name"
    fi
    
    log "Building multi-architecture image: $full_image_name"
    log "Target platforms: $PLATFORMS"
    
    # Build arguments
    local build_args=(
        --platform "$PLATFORMS"
        --tag "$full_image_name"
        --file Dockerfile
        .
    )
    
    # Add push flag if registry is specified
    if [[ -n "$REGISTRY" ]]; then
        build_args+=(--push)
        log "Will push to registry: $REGISTRY"
    else
        build_args+=(--load)
        warn "No registry specified. Building for local use only."
        warn "Note: Multi-arch images cannot be loaded locally. Building for current platform only."
        build_args[1]="linux/$(docker version --format '{{.Server.Arch}}')"
    fi
    
    # Execute build
    log "Executing docker buildx build..."
    docker buildx build "${build_args[@]}"
    
    success "Build completed successfully!"
    
    if [[ -n "$REGISTRY" ]]; then
        success "Image pushed to: $full_image_name"
    else
        success "Image built locally: $full_image_name"
    fi
}

# Function to show usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Build multi-architecture Docker image for globeco-execution-service

OPTIONS:
    -t, --tag TAG           Image tag (default: latest)
    -r, --registry REGISTRY Registry to push to (optional)
    -p, --platforms PLATFORMS Target platforms (default: linux/amd64,linux/arm64)
    -h, --help             Show this help message

EXAMPLES:
    # Build for local use
    $0

    # Build with custom tag
    $0 --tag v1.2.3

    # Build and push to registry
    $0 --tag v1.2.3 --registry your-registry.com/your-org

    # Build for specific platforms
    $0 --platforms linux/amd64

ENVIRONMENT VARIABLES:
    TAG                     Image tag (can be overridden by --tag)
    REGISTRY               Registry URL (can be overridden by --registry)

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -t|--tag)
            TAG="$2"
            shift 2
            ;;
        -r|--registry)
            REGISTRY="$2"
            shift 2
            ;;
        -p|--platforms)
            PLATFORMS="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    log "Starting multi-architecture build for $IMAGE_NAME"
    log "Configuration:"
    log "  Tag: $TAG"
    log "  Registry: ${REGISTRY:-"(none - local build)"}"
    log "  Platforms: $PLATFORMS"
    
    check_buildx
    setup_builder
    build_image
    
    success "Multi-architecture build process completed!"
}

# Run main function
main "$@"