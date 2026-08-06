FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /usr/src/app

COPY . /usr/src/app
RUN mvn package

FROM eclipse-temurin:21-jre-alpine

COPY --from=builder /usr/src/app/target/trip-booking-spa-0.0.1.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java"]
CMD ["-jar", "/app.jar"]
