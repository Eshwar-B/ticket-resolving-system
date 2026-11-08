# =============================================================================
# Dockerfile for ticket-service
# Java 21, Spring Boot 3.x, memory cap: -Xmx256m
# =============================================================================

FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy pre-packaged fat JAR from build artifact directory
COPY ticket-service/target/ticket-service-*.jar app.jar

EXPOSE 8081

# Health check probe
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
