# Time Deposit Kata — Java

Spring Boot application (Java 17, Maven) for the XA Bank time deposit take-home exercise.

## Status

The application is **bootstrapped only**: the Spring Boot framework, the
framework-independent hexagonal application core (ports, services, read models),
and the existing interest calculator are in place, along with the OpenAPI
contract (`src/main/resources/openapi.yaml`).

The deposit HTTP endpoints, database persistence, and the production repository
adapter are **not implemented yet**; implementing them is a later step.

## Build and run

Run the application through Maven (from this `java` directory):

```
mvn spring-boot:run
```

Or build the executable JAR and run it:

```
mvn clean package
java -jar target/time-deposit-kata-1.0-SNAPSHOT.jar
```

The application starts without any database configuration. It currently serves
no deposit endpoints.

## Tests

Run the full unit test suite:

```
mvn clean test
```
