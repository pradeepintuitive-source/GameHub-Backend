# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy only the Maven descriptor first to maximize Docker layer caching for dependencies.
COPY pom.xml ./

# Resolve dependencies in a separate layer so transient network hiccups are less likely to invalidate the full build.
RUN mvn -B -DskipTests dependency:go-offline

# Copy source code after dependencies have been resolved.
COPY src ./src

# Build the project (skip tests for faster builds in production)
RUN mvn -B -DskipTests clean package -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/target/gamehub-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
