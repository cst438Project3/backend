# TransferHelper Backend

Spring Boot backend service with Docker setup for team development.

## Requirements

- Java 21 (for local non-Docker run)
- Docker Desktop (or Docker Engine + Compose)

## Run with Docker (recommended)

From the project root, start backend + PostgreSQL:

```bash
docker compose up --build
```

App will be available at:

- http://localhost:8080

PostgreSQL will be available at:

- host: `localhost`
- port: `5432`
- database: `transferhelper`
- username: `transferhelper`
- password: `transferhelper`

To stop everything:

```bash
docker compose down
```

To stop and remove DB volume too:

```bash
docker compose down -v
```

## Run locally without Docker

```bash
./gradlew bootRun
```

By default, local run uses in-memory H2.

## Configuration notes

The app reads datasource and JPA settings from environment variables with safe defaults in [src/main/resources/application.properties](src/main/resources/application.properties).

This allows:

- local development with H2 by default
- containerized development with PostgreSQL via `docker-compose.yml`

## Testing with JUnit

This project is configured to run tests with JUnit 5 (Jupiter) on Gradle.

Run all tests:

```bash
./gradlew test
```

Run only unit tests (tagged with `unit`):

```bash
./gradlew unitTest
```

Run only integration tests (tagged with `integration`):

```bash
./gradlew integrationTest
```

Current test setup:

- `BackendUnitTests` is a JUnit unit-test example.
- `BackendApplicationTests` is a Spring Boot integration test that uses the `test` profile.