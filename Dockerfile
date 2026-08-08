# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /usr/src/app

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app /home/webrun/logs/file /home/webrun/spa/logs/file \
    && chown -R app:app /app /home/webrun

WORKDIR /app

COPY --from=builder --chown=app:app \
    /usr/src/app/target/trip-booking-spa-0.0.1.jar /app/app.jar

USER app

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
