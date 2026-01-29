# Build stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy only dependency files first for better caching
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached unless build files change)
RUN gradle dependencies --no-daemon

# Copy source and build
COPY src ./src
RUN gradle build -x test --no-daemon

# Runtime stage - minimal JRE image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser

# Cloud Run sets PORT environment variable
ENV PORT=8080
EXPOSE 8080

# JVM optimizations for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
