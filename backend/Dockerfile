# =========================
# Build Stage
# =========================
FROM gradle:9.0.0-jdk21 AS builder

WORKDIR /app

# Gradle 캐시 활용
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x gradlew

COPY src src

# Spring Boot 빌드
RUN ./gradlew bootJar --no-daemon

# =========================
# Runtime Stage
# =========================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]