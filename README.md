# trip-booking-spa

## Project structure

This is a single-module Spring Boot application. Java sources, resources, and
tests live in the standard `src/main` and `src/test` directories.

## Local development

The project builds and runs on Java 21. The local profile uses the existing
`tg-local-mysql` (`127.0.0.1:3307`) and `tg-local-redis`
(`127.0.0.1:6380`) Docker containers, plus the local `trip-booking-spa-nacos`
(`127.0.0.1:8848`) container.

```bash
docker start tg-local-mysql tg-local-redis trip-booking-spa-nacos
mvn -DskipTests package
java -jar target/trip-booking-spa-0.0.1.jar \
  --spring.profiles.active=dev,local \
  --server.port=18089
```

Runtime switches belong in Nacos Data ID `trip-booking-spa.yaml`, group
`DEFAULT_GROUP`. See `config/nacos/trip-booking-spa.yaml.example` for the
expected structure. The local profile contains equivalent fallback values so
the service can start before the dev Data ID is populated.
