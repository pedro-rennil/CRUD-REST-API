# syntax=docker/dockerfile:1

# ---- Build stage: compiles the application and packages the executable jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Resolve dependencies first: this layer is only rebuilt when the pom or the wrapper changes,
# so source-only changes reuse the cached dependencies.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), not while building the image.
RUN ./mvnw -B -q package -DskipTests

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/target/*.jar app.jar
USER app

EXPOSE 8080

# Size the heap from the container's memory limit instead of the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
