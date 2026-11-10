FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml ./
COPY common/ ./common/
COPY notification-service/ ./notification-service/

RUN mvn clean package -pl common,notification-service -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
