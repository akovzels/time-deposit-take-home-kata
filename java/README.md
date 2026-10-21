# Time Deposit Kata — Java

Spring Boot application (Java 17, Maven, PostgreSQL) for the XA Bank time deposit take-home exercise.

All commands below run from the `java` directory.

## Getting Started

Requires a running Docker daemon with Compose and available host ports `8080`
and `5432`. Local Java and Maven installations are not required.

### 1. Start the application

```
docker compose up --build -d
```

Compose builds the Java 17 application and starts PostgreSQL. The application
starts after the database healthcheck succeeds; Flyway creates the schema.
Follow the application logs and wait for `Started TimeDepositApplication`:

```
docker compose logs -f app
```

Press `Ctrl+C` to stop following the logs; the containers keep running.

### 2. Load sample deposits (optional)

After application startup, load [sample-data.sql](sample-data.sql) into the
database:

```
docker compose cp ./sample-data.sql postgres:/tmp/time-deposit-sample-data.sql
docker compose exec -T postgres psql -U time_deposit -d time_deposit -v ON_ERROR_STOP=1 -f /tmp/time-deposit-sample-data.sql
```

The script adds eight deposits and two withdrawals in one transaction, with
generated IDs. It covers eligibility boundaries, an unknown plan, and deposits
with and without withdrawals. Data is loaded manually; running the script again
adds another set without changing existing rows.

### 3. Open Swagger UI

Open [Swagger UI](http://localhost:8080/swagger-ui.html) in your browser.

1. Call **GET `/api/v1/time-deposits`** to view all time deposits.
2. Call **POST `/api/v1/time-deposits/balance-updates`** to update balances.

After one POST, the sample balances are:

| Plan | Days | Before | After |
|---|---:|---:|---:|
| basic | 30 | 1200.00 | 1200.00 |
| basic | 31 | 1200.00 | 1201.00 |
| student | 31 | 402.00 | 403.00 |
| student | 365 | 1200.00 | 1203.00 |
| student | 366 | 1200.00 | 1200.00 |
| premium | 45 | 1200.00 | 1200.00 |
| premium | 46 | 1200.00 | 1205.00 |
| business | 1000 | 1200.00 | 1200.00 |

The student result preserves the original calculator's double arithmetic and
rounding. Every additional POST applies another calculation. Days, plan types,
and withdrawals remain unchanged.

### 4. Stop the application

Stop the containers; database data remains in the named volume and is reused on
the next startup:

```
docker compose down
```

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

Requires Java 17, Maven, and PostgreSQL. Start only the database with
`docker compose up -d --wait postgres`. If the Docker application is already
running, stop it with `docker compose stop app` to free port `8080`.

```
mvn spring-boot:run
```

or

```
mvn clean package
java -jar target/time-deposit-kata-1.0-SNAPSHOT.jar
```

## Architecture

Hexagonal architecture keeps the calculator and application services independent
of Spring. HTTP controllers call inbound use-case ports; services depend on the
outbound repository port, implemented by the PostgreSQL JDBC adapter. Spring
configuration wires the components. A transaction decorator wraps the update
use case, covering loading with `SELECT ... FOR UPDATE`, calculation, and saving.
Read models carry decimal amounts from the database to the HTTP response.

## Assumptions and trade-offs

- The assignment describes "monthly interest" with rates of 1%, 3%, and 5%,
  but the existing implementation divides those rates by 12. Since the assignment
  explicitly requires preserving its behavior, the rates are interpreted as
  annual, with one month's interest applied per eligible calculation.
- Eligibility boundaries follow the existing implementation: basic starts on
  day 31, student applies on days 31–365 inclusive, and premium starts on day 46.
  "One year" is interpreted as 365 elapsed days. Unknown plans earn no interest.
- Binary floating-point (`double`) can produce inaccurate monetary calculations.
  The assignment requires preserving the shared `TimeDeposit` contract and the
  observable behavior of `updateBalance`, so this limitation remains. For example,
  student interest on `402.00` is mathematically `402 × 0.03 / 12 = 1.005`, which
  rounds HALF_UP to `1.01`, giving `403.01`. The existing double calculation
  produces a value slightly below `1.005`; `new BigDecimal(double)` preserves
  that approximation, so interest rounds to `1.00` and the balance becomes
  `403.00`. Without the compatibility constraint, use `BigDecimal` throughout
  monetary calculations, or store balances as integer minor units (for example,
  cents in a `long` or `BigInteger`). Integer storage is exact, but fractional
  interest still requires precise arithmetic and an explicit rounding policy.
  Database storage and read models already use decimal amounts; this does not
  correct the calculator's double arithmetic.
- Every successful POST applies another calculation. There is no period tracking
  or scheduling; `days` is supplied externally and is not updated here.
- A single transaction is acceptable for a small dataset. A production design
  would process deposits concurrently in separate transactions, track opening
  dates and processed accrual periods, and calculate interest from daily balances
  and effective plan terms. Unique deposit/period records would prevent duplicate
  accruals, while per-deposit locking would protect concurrent balance changes.

The [OpenAPI contract](src/main/resources/openapi.yaml) defines the operations,
response schemas, and Problem Details errors. The [AI workflow](AI_WORKFLOW.md)
describes tools, responsibilities, and the prompts used during development.

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

