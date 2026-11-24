# ==========================================
# Stage 1: Build Spring Boot WebFlux JAR
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Production Runtime (Java 21 JRE)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Configure America/Sao_Paulo timezone for DailyBillingJob scheduler
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/America/Sao_Paulo /etc/localtime && \
    echo "America/Sao_Paulo" > /etc/timezone

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Sao_Paulo"
ENV SERVER_PORT=8080
ENV SEED_SAMPLE_DATA=false

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
