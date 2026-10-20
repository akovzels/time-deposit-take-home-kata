# Time Deposit Kata — Java

Spring Boot application (Java 17, Maven, PostgreSQL) for the XA Bank time deposit take-home exercise.

## Status

Implemented so far: the Spring Boot bootstrap, the framework-independent hexagonal
application core (ports, services, read models), the existing interest calculator,
the OpenAPI contract (`src/main/resources/openapi.yaml`), and the PostgreSQL
persistence adapter (`JdbcTimeDepositRepository` with Flyway migrations under
`src/main/resources/db/migration`).

**Application startup now requires PostgreSQL.** The deposit HTTP endpoints,
application-service wiring, and the production transaction wrapper are
**not implemented yet**.

## Database (local development)

Start PostgreSQL (pinned image, named data volume):

```
docker compose up -d
```

## Configuration

The datasource is configured through environment variables, with
local-development defaults matching the Compose file:

| Variable | Default |
|---|---|
| `TIME_DEPOSIT_DB_URL` | `jdbc:postgresql://localhost:5432/time_deposit` |
| `TIME_DEPOSIT_DB_USERNAME` | `time_deposit` |
| `TIME_DEPOSIT_DB_PASSWORD` | `time_deposit` |

Flyway applies the real migrations at application startup.

## Application startup

```
mvn spring-boot:run
```

or

```
mvn clean package
java -jar target/time-deposit-kata-1.0-SNAPSHOT.jar
```

## Tests

Ordinary unit tests (no Docker required):

```
mvn test
```

Full verification, including the PostgreSQL integration tests
(Testcontainers; requires a running Docker daemon):

```
mvn verify
```

