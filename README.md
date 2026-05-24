# Multi-Region Read-Heavy Profile Service

Production-grade Java/Spring implementation for globally low-latency user profile reads with one primary write region, Kafka-based profile change replication, regional Postgres read models and Redis cache.

## GitHub repository

**Repository name:** `multi-region-profile-service`

**Description:** `Spring Boot 4 / Java 25 profile service for multi-region read-heavy workloads with primary Postgres writes, Kafka event replication, regional Postgres read models, Redis cache, read-your-writes guards, projection watermarks, hardened outbox retries, tests and Postman collection.`

## Chosen environment

| Area | Choice |
| --- | --- |
| Java | Java 25 toolchain |
| Framework | Spring Boot 4.0.6, WebFlux |
| Build | Gradle 9.x |
| Database | PostgreSQL + R2DBC |
| Migrations | Flyway |
| Events | Apache Kafka in KRaft mode |
| Cache | Redis reactive cache-aside |
| Tests | JUnit 5, Reactor Test, Mockito, Testcontainers-ready integration base |
| Observability | Actuator, Micrometer, Prometheus endpoint, replication lag endpoint, projection watermarks |
| Security | API-key guard, constant-time key comparison, correlation ID, Redis rate limiting |

## Architecture

```text
                 ┌─────────────────────────────────────────────┐
                 │                 Write Region                │
                 │                                             │
Client writes ──▶│ WebFlux API ──▶ Primary Postgres profiles   │
                 │                  + transactional outbox      │
                 │                         │                   │
                 │             SKIP LOCKED publisher           │
                 │                         ▼                   │
                 │                 Kafka profile.changes        │
                 └─────────────────────────┬───────────────────┘
                                           │ MirrorMaker 2 / managed Kafka replication
        ┌──────────────────────────────────┴──────────────────────────────────┐
        ▼                                                                     ▼
┌─────────────────────────────┐                                 ┌─────────────────────────────┐
│ Read Region EU              │                                 │ Read Region US              │
│ Kafka consumer              │                                 │ Kafka consumer              │
│  ├─ local Postgres read DB   │                                 │  ├─ local Postgres read DB   │
│  ├─ projection watermark     │                                 │  ├─ projection watermark     │
│  └─ Redis regional cache     │                                 │  └─ Redis regional cache     │
│ Reads stay in region         │                                 │ Reads stay in region         │
└─────────────────────────────┘                                 └─────────────────────────────┘
```

## Implemented production upgrades

* Fixed API-key error JSON bug and changed API-key comparison to constant-time byte comparison.
* Added Redis-backed fixed-window rate limiting for read, write and internal endpoints.
* Added `X-Correlation-Id` propagation for tracing requests across regions.
* Hardened transactional outbox for multi-pod primary deployments using atomic row claiming with `FOR UPDATE SKIP LOCKED`.
* Added retry backoff, stale outbox lock reclaim, terminal `EXHAUSTED` state and `/internal/outbox/stats`.
* Added Kafka projection watermarks per topic/partition/offset and changed lag calculation to prefer watermarks.
* Added invalid-event quarantine table and `/internal/projection/invalid-events` to avoid endless retries for malformed events.
* Added `minVersion` read guard for read-your-writes clients: `GET /api/v1/profiles/{id}?minVersion=3` returns `READ_MODEL_BEHIND` when the region is stale.
* Added Flyway V3 migration and updated Postman collection with all new operational endpoints.

## Consistency SLA

Default SLA: **profile changes visible in each read region within 5 seconds** after the event timestamp reaches the regional Kafka topic.

The implementation exposes:

* `GET /api/v1/consistency/sla` — configured consistency SLA.
* `GET /internal/replication/lag` — lag calculated from projection watermarks where available.
* `GET /internal/projection/watermarks` — last consumed Kafka offset per regional partition.
* Read responses include `replicationLagSeconds` and `withinSla`.
* `GET /api/v1/profiles/{profileId}?minVersion={version}` — read-your-writes guard for callers that know the required profile version.

## Local startup

```bash
docker compose up --build
```

Services:

| Service | URL |
| --- | --- |
| Primary API | `http://localhost:8080` |
| EU read API | `http://localhost:8081` |
| US read API | `http://localhost:8082` |
| Kafka | `localhost:9092` |
| Primary Postgres | `localhost:5432` |
| EU read Postgres | `localhost:5433` |
| US read Postgres | `localhost:5434` |

Default headers:

```text
X-API-Key: local-api-key
X-Internal-Key: local-internal-key
X-Correlation-Id: optional-client-generated-id
```

## Typical flow

```bash
curl -X POST http://localhost:8080/api/v1/profiles \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: local-api-key' \
  -H 'X-Correlation-Id: demo-1' \
  -d '{"email":"alice@example.com","displayName":"Alice","phone":"+48100100200"}'
```

Then read from a local read region:

```bash
curl http://localhost:8081/api/v1/profiles/<profile-id> \
  -H 'X-API-Key: local-api-key'
```

Read with a minimum expected version:

```bash
curl 'http://localhost:8081/api/v1/profiles/<profile-id>?minVersion=2' \
  -H 'X-API-Key: local-api-key'
```

Operational checks:

```bash
curl http://localhost:8081/internal/replication/lag -H 'X-Internal-Key: local-internal-key'
curl http://localhost:8081/internal/projection/watermarks -H 'X-Internal-Key: local-internal-key'
curl http://localhost:8080/internal/outbox/stats -H 'X-Internal-Key: local-internal-key'
```

## Test commands

```bash
./gradlew clean test
./gradlew bootJar
./scripts/check-static.sh
./scripts/smoke-test.sh
```

The included `gradlew` is a small sandbox-safe shim because this generated archive cannot download the official wrapper JAR. In a real repository, generate and commit the official Gradle wrapper:

```bash
gradle wrapper --gradle-version 9.5.1
```

## Production notes

* Use one writable deployment with `APP_REGION_ROLE=PRIMARY`.
* Use one or more read-region deployments with `APP_REGION_ROLE=READ_REPLICA`.
* Use Kafka MirrorMaker 2, Cluster Linking or a managed Kafka replication feature between regions.
* Keep topic key as `profileId` to preserve per-profile ordering.
* Run at least as many read-region consumers as partitions only after validating consumer lag and DB write capacity.
* Use short Redis TTLs for hot profiles and `minVersion` guards for critical read-your-writes flows.
* Monitor `profile_replication_lag_seconds`, outbox `EXHAUSTED` count, invalid projection events, Kafka consumer lag, rate-limit rejections and cache hit ratio.
