FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml ./
COPY common/ ./common/
COPY usage-audit-service/ ./usage-audit-service/

RUN mvn clean package -pl common,usage-audit-service -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/usage-audit-service/target/usage-audit-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
