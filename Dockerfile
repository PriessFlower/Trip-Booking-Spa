ARG RUNTIME_IMAGE=ccr.ccs.tencentyun.com/priessflower/trip-booking-spa:runtime-base-jre21
FROM ${RUNTIME_IMAGE}

WORKDIR /app

COPY --chown=app:app target/trip-booking-spa-0.0.1.jar /app/app.jar

USER app

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
