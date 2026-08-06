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

## Expedia Rapid development

Expedia credentials are not stored in YAML. Copy the variable names from
`.env.example` into your shell or IntelliJ run configuration. The default host
is the Rapid test endpoint and booking is disabled. Static Content ingestion,
field mapping, evidence and the local table definition are documented in
`docs/expedia-static-data.md`.

Legacy supplier beans currently inherit safe mock configuration from
`application.yml`. Set `MOCK_SUPPLIER_BASE_URL` only when a local mock server is
available; no former supplier endpoint or credential is retained in profile
configuration.
