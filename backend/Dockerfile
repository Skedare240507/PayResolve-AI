# ==============================================================================
# PayResolve Backend Dockerfile (Multi-stage build, Eclipse Temurin 21)
# ==============================================================================

# Stage 1: Build & Package
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Maven wrapper & POM
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw

# Copy source code
COPY src src

# The real application.yml is not committed; copy the example before building
RUN cp src/main/resources/application.yml.example src/main/resources/application.yml

# Package application (tests run in CI against test resources)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal Runtime Image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as non-privileged user
RUN groupadd -r payresolve && useradd -r -g payresolve -m -s /bin/bash payresolve
USER payresolve

# Copy built artifact from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Container configuration: 512MB RAM budget (70% MaxRAMPercentage = ~358MB JVM heap)
ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Djava.net.preferIPv4Stack=true"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
