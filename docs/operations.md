# Operations Guide

## Key metrics and endpoints

* `profile_replication_lag_seconds` — latest event age based on projection watermarks where available.
* `GET /internal/replication/lag` — region, lag, SLA status and lag source.
* `GET /internal/projection/watermarks` — topic/partition/offset per read-region projection.
* `GET /internal/projection/invalid-events?limit=50` — malformed payload quarantine.
* `GET /internal/outbox/stats` — outbox counts by `NEW`, `IN_PROGRESS`, `FAILED`, `EXHAUSTED`, `PUBLISHED`.
* Kafka consumer lag — per region and consumer group.
* Redis cache hit ratio — compare read-model DB reads with cache hits.
* HTTP p95/p99 latency per region and rate-limit rejections.

## Regional deployment

Primary region:

```text
APP_REGION_NAME=eu-central-1
APP_REGION_ROLE=PRIMARY
APP_KAFKA_CONSUMER_ENABLED=false
APP_OUTBOX_ENABLED=true
```

Read region:

```text
APP_REGION_NAME=us-east-1
APP_REGION_ROLE=READ_REPLICA
APP_KAFKA_CONSUMER_ENABLED=true
APP_OUTBOX_ENABLED=false
```

## Kafka replication

Use MirrorMaker 2, managed cluster linking or cloud-provider Kafka replication. Replicate only profile topics from primary to read regions unless local operational topics are needed.

Recommended topic naming:

```text
profile.changes.v1
```

Keep the message key as `profileId`. This preserves per-profile ordering and makes stale event protection deterministic.

## Rate limiting

Default local limits:

```text
APP_RATE_LIMIT_ENABLED=true
APP_RATE_LIMIT_WINDOW=60s
APP_RATE_LIMIT_READ_LIMIT=1200
APP_RATE_LIMIT_WRITE_LIMIT=120
APP_RATE_LIMIT_INTERNAL_LIMIT=300
```

The limiter is Redis-backed and fail-open. Put stricter global protection at the edge gateway or WAF for internet-facing deployments.

## Runbook

### Read region lag exceeds SLA

1. Check `GET /internal/replication/lag` in the affected region.
2. Check `GET /internal/projection/watermarks` and compare offsets with Kafka consumer lag.
3. Check regional Kafka consumer errors and local Postgres write latency.
4. Check invalid event quarantine for malformed messages.
5. Temporarily reduce Redis TTL if stale cache risk is suspected.
6. Use `minVersion` in critical clients that must avoid stale read-your-writes responses.

### Outbox backlog grows

1. Check Kafka broker availability from the primary region.
2. Check `GET /internal/outbox/stats`.
3. Inspect `profile_outbox.last_error` for `FAILED` and `EXHAUSTED` rows.
4. Use `POST /internal/outbox/replay?limit=100` after broker recovery.
5. Alert if backlog age exceeds consistency SLA plus operational threshold.

### Invalid projection events appear

1. Call `GET /internal/projection/invalid-events?limit=50`.
2. Inspect payload schema version and source service deployment version.
3. Fix the producer or schema compatibility issue.
4. Re-publish corrected events manually only after validating aggregate version ordering.
