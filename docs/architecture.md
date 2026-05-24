# Architecture Notes

## Selected approach

The service uses CQRS plus transactional outbox:

1. Writes are accepted only in the primary region.
2. The primary transaction writes the profile row and an outbox row in the same Postgres transaction.
3. A scheduled publisher atomically claims outbox records with `FOR UPDATE SKIP LOCKED` and sends them to Kafka using `profileId` as the message key.
4. Regional consumers project events into a local Postgres read model.
5. Regional consumers update projection watermarks so lag can be measured by Kafka position.
6. Regional reads use Redis first, then local Postgres, never the primary database.

## Why this is production-friendly

* The write path has one source of truth and avoids active-active profile conflicts.
* The outbox prevents lost events when the application crashes after DB commit but before Kafka publish.
* Outbox claiming is safe for multiple primary pods; stale locks are reclaimable after `app.outbox.lock-timeout`.
* Kafka keying by `profileId` preserves per-profile ordering within a partition.
* Read regions remain available and fast even when the primary write region is far away.
* Read models are idempotent and ignore older event versions.
* Redis is regional and disposable; Postgres read model remains the durable local read source.
* Projection watermarks avoid misleading lag metrics when no profile rows changed recently.

## Consistency model

The service is eventually consistent. A profile update is considered visible in a read region when the consumer has applied the change event to `profile_read_model` and refreshed/evicted the regional Redis entry.

Default SLA: 5 seconds.

For read-your-writes flows, clients can pass `minVersion` on the read endpoint. If the regional view has not reached that version, the service returns `409 READ_MODEL_BEHIND` rather than silently returning stale data.

## Failure handling

* Outbox rows are retried with exponential backoff until Kafka publish succeeds or the retry budget is exhausted.
* Exhausted outbox rows are marked `EXHAUSTED` and visible through `/internal/outbox/stats`.
* Consumers are idempotent through `consumed_profile_events` and version-checked read model upserts.
* Malformed Kafka payloads are written to `invalid_profile_events` so they can be inspected without endless retry loops.
* Regional read API returns read-model metadata so clients and operators can detect stale data.

## Data model additions

* `profile_projection_watermark` tracks topic, partition, offset, latest event time and last local consumption time.
* `invalid_profile_events` quarantines malformed profile events with raw payload and safe error text.
* `profile_outbox` includes retry/backoff/lock metadata: `next_attempt_at`, `locked_by`, `locked_at`, `dead_lettered_at`.
