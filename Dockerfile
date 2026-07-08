# syntax=docker/dockerfile:1
# Multi-architecture optimized Dockerfile with CDS and AOT for fast startup

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

# Build the application with AOT processing (dependencies already cached)
RUN ./gradlew clean bootJar --no-daemon --build-cache

# ---- CDS Training Stage ----
# Generate a CDS (Class Data Sharing) archive by performing a training run.
# This pre-loads class metadata so the JVM skips parsing/verification at runtime.
FROM eclipse-temurin:21-jre-jammy AS cds
WORKDIR /app

COPY --from=build /workspace/app/build/libs/*.jar app.jar

# Perform a CDS training run:
# -XX:ArchiveClassesAtExit dumps all loaded classes into a shared archive
# -Dspring.context.exit=onRefresh exits after context refresh (no live DB needed)
# -Dspring.aot.enabled=true uses the AOT-generated bean definitions
# The process exits non-zero by design (onRefresh triggers exit), so we use || true
# then verify the archive was created
RUN java -XX:ArchiveClassesAtExit=application.jsa \
         -Dspring.context.exit=onRefresh \
         -Dspring.aot.enabled=true \
         -jar app.jar ; \
    test -f application.jsa && echo "CDS archive created successfully" || (echo "ERROR: CDS archive not created" && exit 1)

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the application jar and CDS archive
COPY --from=cds /app/app.jar /app/app.jar
COPY --from=cds /app/application.jsa /app/application.jsa

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check (reduced start-period since startup is now much faster)
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

EXPOSE 8084

# Use exec form for better signal handling
# -XX:SharedArchiveFile loads the pre-computed CDS archive
# -Dspring.aot.enabled=true activates AOT-generated bean definitions
# -XX:TieredStopAtLevel=1 skips C2 JIT for faster startup
# -XX:+UseSerialGC minimal GC overhead during startup
# -Xss256k smaller thread stacks for faster thread creation
ENTRYPOINT ["java", \
    "-XX:SharedArchiveFile=application.jsa", \
    "-Dspring.aot.enabled=true", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:TieredStopAtLevel=1", \
    "-XX:+UseSerialGC", \
    "-Xss256k", \
    "-jar", "app.jar"]
