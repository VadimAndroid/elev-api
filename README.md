# elev-api

[![CI](https://github.com/VadimAndroid/elev-api/actions/workflows/ci.yml/badge.svg?branch=portfolio)](https://github.com/VadimAndroid/elev-api/actions/workflows/ci.yml)

The entry point of the **elev** system. A Spring Boot service in Kotlin that accepts requests from clients,
publishes new students (*elever*) to **Kafka** and reads them from [elev-service](https://github.com/VadimAndroid/elev-service/tree/portfolio) over **HTTP**.
`elev-api` has **no database**.

> This is the **`portfolio`** branch. The original school assignment (Norwegian README) is on `main`.

| Repository | Role |
|---|---|
| **elev-api** (this) | entry point for clients, port 8080 |
| [elev-service](https://github.com/VadimAndroid/elev-service/tree/portfolio) | stores students in PostgreSQL, port 8081 |
| [elev-infra](https://github.com/VadimAndroid/elev-infra) | `docker-compose.yaml` for Kafka, PostgreSQL and both services, architecture overview |

## Tech stack

Kotlin 2.3 · Java 25 · Spring Boot 4.1 · Spring Web MVC · Spring Kafka · Bean Validation · RestClient ·
Spring Boot Actuator · JUnit 5 · MockMvc · Mockito · Docker · GitHub Actions

## API

| Method | Path | Response |
|--------|------|----------|
| POST | `/elever` | `202` with the student (incl. id) · `400` if `navn`/`klasse` is blank · `503` if Kafka fails |
| GET | `/elever` | `200` with a list (possibly `[]`) · `503` if `elev-service` is down |
| GET | `/elever/{id}` | `200` · `404` if not found · `503` if `elev-service` is down |
| GET | `/actuator/health` | `200` `{"status":"UP"}` |

Request body: `{ "navn": "Ola Nordmann", "klasse": "10A" }`

## Contract with elev-service

| Channel | Direction | Details |
|---|---|---|
| Kafka topic `elever` | elev-api → elev-service | key: student id (UUID as text) · value: `{"id":"<uuid>","navn":"<text>","klasse":"<text>"}` |
| HTTP `GET {ELEV_SERVICE_URL}/elever[/{id}]` | elev-api → elev-service | `200` with JSON, or `404` |

The services share **no code**. Each has its own `Elev` class; only the JSON format binds them.

## How it works

- **Create is asynchronous.** `POST /elever` validates the input (`@Valid`, `@NotBlank`), generates a UUID, trims the values and
  publishes the student as JSON with the id as the message key. It waits until Kafka **acknowledges** the record,
  then returns **202 Accepted**: the student is accepted, but maybe not stored yet. If Kafka does not answer within
  5 seconds (`max.block.ms`), the response is **503** with an error message.
- **Read is synchronous.** `GET` calls `elev-service` with `RestClient`. A `404` from `elev-service` becomes `404` here.
  If `elev-service` cannot be reached, the response is **503**, not an empty list, because an empty list would be wrong information.

## Configuration

| Variable | Local default | In Docker |
|----------|---------------|-----------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | `kafka:29092` |
| `ELEV_SERVICE_URL` | `http://localhost:8081` | `http://elev-service:8081` |

## Running

Start Kafka from [elev-infra](https://github.com/VadimAndroid/elev-infra) (`docker compose up -d`), start `elev-service`, then:

```bash
./gradlew bootRun
```

or ▶ next to `fun main` in `ElevApiApplication.kt`. `requests.http` contains ready-made requests for IntelliJ.
To run everything in Docker, see [elev-infra](https://github.com/VadimAndroid/elev-infra).

## Tests

```bash
./gradlew test
```

Web layer tests with `@WebMvcTest` and Mockito mocks: `202` (and input is trimmed) · `400` and nothing sent to Kafka ·
`503` when Kafka fails · `200` · `404` · `503` when `elev-service` is down.

## Docker image

Multi-stage `Dockerfile`: the jar is built with Gradle in `eclipse-temurin:25-jdk` and runs in the smaller
`eclipse-temurin:25-jre` as a non-root user.

## CI

`.github/workflows/ci.yml` runs `./gradlew test` on every push and pull request, and then checks that the Docker image builds.

## Structure

```
src/main/kotlin/com/example/elev_api/
├── client/ElevServiceClient.kt   – HTTP calls to elev-service (RestClient)
├── controller/ElevController.kt  – endpoints, 202 / 404 / 503
├── kafka/ElevProducer.kt         – publishes to Kafka and waits for the ack
├── model/Elev.kt
├── model/OpprettElevRequest.kt   – request body with @NotBlank
└── service/ElevService.kt        – UUID, trim, Kafka for create, HTTP for read
```
