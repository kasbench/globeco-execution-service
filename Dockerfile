# syntax=docker/dockerfile:1
# Multi-architecture optimized Dockerfile for fast builds

# ---- Dependencies Stage ----
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS deps
WORKDIR /workspace/app

# Copy dependency files first for better caching
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# Download dependencies (this layer will be cached unless dependencies change)
RUN ./gradlew --no-daemon dependencies

# ---- Build Stage ----
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/app

# Copy cached dependencies from deps stage
COPY --from=deps /root/.gradle /root/.gradle
COPY --from=deps /workspace/app/gradle gradle/
COPY --from=deps /workspace/app/gradlew ./
COPY --from=deps /workspace/app/build.gradle ./
COPY --from=deps /workspace/app/settings.gradle ./

# Copy source code
COPY src/ src/

# Build the application (dependencies already cached)
RUN ./gradlew clean bootJar --no-daemon --build-cache

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built JAR
COPY --from=build /workspace/app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown appuser:appuser app.jar

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

EXPOSE 8084

# Use exec form for better signal handling
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"] 