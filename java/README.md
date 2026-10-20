# Time Deposit Kata — Java

Spring Boot application (Java 17, Maven, PostgreSQL) for the XA Bank time deposit take-home exercise.

## Status

Implemented so far: the Spring Boot bootstrap, the framework-independent hexagonal
application core (ports, services, read models), the existing interest calculator,
the OpenAPI contract (`src/main/resources/openapi.yaml`), the PostgreSQL
persistence adapter (`JdbcTimeDepositRepository` with Flyway migrations under
`src/main/resources/db/migration`), and the retrieval endpoint
(`GET /api/v1/time-deposits`, see the API section below).

**Application startup now requires PostgreSQL.** The balance-update endpoint
and its production transaction wrapper are **not implemented yet**.

All commands below run from the `java` directory.

## Run with Docker

Requires Docker with Compose. Build the application image and start the application
and PostgreSQL:

```
docker compose up --build -d
```

The multi-stage Dockerfile builds the executable JAR with Maven and Java 17,
then runs it as a non-root user in a Java 17 runtime image. Tests are run separately
using the commands below. Local Java and Maven installations are not required for
this Docker workflow.

The application waits for PostgreSQL's healthcheck before starting and connects
to the `postgres` service. Port `8080` is published on the host; the retrieval
endpoint is available and the balance-update endpoint is not implemented yet.
Follow application startup logs with:

```
docker compose logs -f app
```

Stop the containers (database data remains in the named volume):

```
docker compose down
```

## Database (local development)

To run the application locally instead, start only PostgreSQL and wait for it to
be ready:

```
docker compose up -d --wait postgres
```

If the Docker application is already running, stop it with `docker compose stop app`
before starting a local application on the same port.

## Configuration

The datasource is configured through environment variables, with
local-development defaults matching the database exposed by Compose. The Docker
application overrides the URL to use the `postgres` service hostname:

| Variable | Default |
|---|---|
| `TIME_DEPOSIT_DB_URL` | `jdbc:postgresql://localhost:5432/time_deposit` |
| `TIME_DEPOSIT_DB_USERNAME` | `time_deposit` |
| `TIME_DEPOSIT_DB_PASSWORD` | `time_deposit` |

Flyway applies the real migrations at application startup.

## Application startup (without Docker)

Requires Java 17, Maven, and the running PostgreSQL instance described above.

```
mvn spring-boot:run
```

or

```
mvn clean package
java -jar target/time-deposit-kata-1.0-SNAPSHOT.jar
```

## API

Retrieval is implemented; balance updates are not implemented yet.

Example GET request:

```
curl http://localhost:8080/api/v1/time-deposits
```

Example response:

```json
[
  {
    "id": 1,
    "planType": "student",
    "balance": 1002.50,
    "days": 365,
    "withdrawals": [
      { "id": 1, "timeDepositId": 1, "amount": 50.25, "date": "2026-06-15" }
    ]
  },
  {
    "id": 2,
    "planType": "premium",
    "balance": 1004.17,
    "days": 46,
    "withdrawals": []
  }
]
```

The full contract is defined in `src/main/resources/openapi.yaml`.

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

