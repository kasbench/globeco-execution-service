# ---- Build Stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/app
COPY . .
RUN ./gradlew clean bootJar --no-daemon

# ---- Run Stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"] 