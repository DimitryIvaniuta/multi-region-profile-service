package com.github.dimitryivaniuta.profile.persistence;

import com.github.dimitryivaniuta.profile.domain.OutboxStatus;
import com.github.dimitryivaniuta.profile.domain.ProfileEventType;
import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive SQL gateway for write model, outbox, projection metadata and read-model persistence.
 *
 * <p>Explicit SQL is used instead of generated repository saves because the service uses assigned
 * UUID identifiers, optimistic version checks, Postgres upsert semantics and outbox row claiming
 * with {@code FOR UPDATE SKIP LOCKED}.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProfileStore {

    private final DatabaseClient databaseClient;

    /**
     * Finds a profile by identifier from the primary write model.
     *
     * @param profileId profile identifier
     * @return profile row when present
     */
    public Mono<ProfileRow> findProfileById(UUID profileId) {
        return databaseClient.sql("""
                SELECT id, email, display_name, phone, status, version, created_at, updated_at
                FROM profiles
                WHERE id = :id
                """)
                .bind("id", profileId)
                .map((row, metadata) -> mapProfile(row))
                .one();
    }

    /**
     * Finds a profile by normalized email from the primary write model.
     *
     * @param email normalized email address
     * @return profile row when present
     */
    public Mono<ProfileRow> findProfileByEmail(String email) {
        return databaseClient.sql("""
                SELECT id, email, display_name, phone, status, version, created_at, updated_at
                FROM profiles
                WHERE email = :email
                """)
                .bind("email", email)
                .map((row, metadata) -> mapProfile(row))
                .one();
    }

    /**
     * Inserts a new profile row into the primary write model.
     *
     * @param row profile row to insert
     * @return inserted row
     */
    public Mono<ProfileRow> insertProfile(ProfileRow row) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO profiles (id, email, display_name, phone, status, version, created_at, updated_at)
                VALUES (:id, :email, :displayName, :phone, :status, :version, :createdAt, :updatedAt)
                """)
                .bind("id", row.id())
                .bind("email", row.email())
                .bind("displayName", row.displayName())
                .bind("status", row.status().name())
                .bind("version", row.version())
                .bind("createdAt", row.createdAt())
                .bind("updatedAt", row.updatedAt());
        spec = bindNullable(spec, "phone", row.phone(), String.class);
        return spec.fetch().rowsUpdated().thenReturn(row);
    }

    /**
     * Updates a profile using an optimistic expected previous version.
     *
     * @param row new profile state
     * @param expectedPreviousVersion previous aggregate version expected in the database
     * @return true when the update won the optimistic version check
     */
    public Mono<Boolean> updateProfile(ProfileRow row, long expectedPreviousVersion) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE profiles
                SET display_name = :displayName,
                    phone = :phone,
                    status = :status,
                    version = :version,
                    updated_at = :updatedAt
                WHERE id = :id AND version = :expectedPreviousVersion
                """)
                .bind("id", row.id())
                .bind("displayName", row.displayName())
                .bind("status", row.status().name())
                .bind("version", row.version())
                .bind("updatedAt", row.updatedAt())
                .bind("expectedPreviousVersion", expectedPreviousVersion);
        spec = bindNullable(spec, "phone", row.phone(), String.class);
        return spec.fetch().rowsUpdated().map(updated -> updated == 1);
    }

    /**
     * Inserts an outbox row in the same transaction as a profile mutation.
     *
     * @param row outbox row to persist
     * @return persisted row
     */
    public Mono<OutboxRow> insertOutbox(OutboxRow row) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO profile_outbox
                    (event_id, aggregate_id, aggregate_version, event_type, payload, status, attempts,
                     last_error, created_at, published_at, next_attempt_at, locked_by, locked_at, dead_lettered_at)
                VALUES
                    (:eventId, :aggregateId, :aggregateVersion, :eventType, :payload, :status, :attempts,
                     :lastError, :createdAt, :publishedAt, :nextAttemptAt, :lockedBy, :lockedAt, :deadLetteredAt)
                """)
                .bind("eventId", row.eventId())
                .bind("aggregateId", row.aggregateId())
                .bind("aggregateVersion", row.aggregateVersion())
                .bind("eventType", row.eventType().name())
                .bind("payload", row.payload())
                .bind("status", row.status().name())
                .bind("attempts", row.attempts())
                .bind("createdAt", row.createdAt());
        spec = bindNullable(spec, "lastError", row.lastError(), String.class);
        spec = bindNullable(spec, "publishedAt", row.publishedAt(), Instant.class);
        spec = bindNullable(spec, "nextAttemptAt", row.nextAttemptAt(), Instant.class);
        spec = bindNullable(spec, "lockedBy", row.lockedBy(), String.class);
        spec = bindNullable(spec, "lockedAt", row.lockedAt(), Instant.class);
        spec = bindNullable(spec, "deadLetteredAt", row.deadLetteredAt(), Instant.class);
        return spec.fetch().rowsUpdated().thenReturn(row);
    }

    /**
     * Atomically claims publishable outbox rows for a single publisher instance.
     *
     * <p>The CTE uses {@code FOR UPDATE SKIP LOCKED} to make the publisher horizontally safe: several
     * primary-region instances can run the scheduler without selecting the same rows.</p>
     *
     * @param maxAttempts maximum retry attempts
     * @param limit maximum number of records to claim
     * @param lockedBy publisher instance identifier
     * @param now claim timestamp
     * @param staleLockBefore in-progress rows locked before this timestamp are reclaimable
     * @return claimed outbox rows ordered by creation time
     */
    public Flux<OutboxRow> claimPublishableOutbox(
            int maxAttempts,
            int limit,
            String lockedBy,
            Instant now,
            Instant staleLockBefore
    ) {
        return databaseClient.sql("""
                WITH candidates AS (
                    SELECT event_id
                    FROM profile_outbox
                    WHERE attempts < :maxAttempts
                      AND (
                            (status IN ('NEW', 'FAILED') AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                         OR (status = 'IN_PROGRESS' AND locked_at < :staleLockBefore)
                      )
                    ORDER BY created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE profile_outbox outbox
                SET status = 'IN_PROGRESS', locked_by = :lockedBy, locked_at = :now
                FROM candidates
                WHERE outbox.event_id = candidates.event_id
                RETURNING outbox.event_id, outbox.aggregate_id, outbox.aggregate_version, outbox.event_type,
                          outbox.payload, outbox.status, outbox.attempts, outbox.last_error,
                          outbox.created_at, outbox.published_at, outbox.next_attempt_at,
                          outbox.locked_by, outbox.locked_at, outbox.dead_lettered_at
                """)
                .bind("maxAttempts", maxAttempts)
                .bind("limit", limit)
                .bind("lockedBy", lockedBy)
                .bind("now", now)
                .bind("staleLockBefore", staleLockBefore)
                .map((row, metadata) -> mapOutbox(row))
                .all();
    }

    /**
     * Marks an outbox row as published after Kafka acknowledges the record.
     *
     * @param eventId event identifier
     * @param publishedAt acknowledgement timestamp
     * @return completion signal
     */
    public Mono<Void> markOutboxPublished(UUID eventId, Instant publishedAt) {
        return databaseClient.sql("""
                UPDATE profile_outbox
                SET status = 'PUBLISHED',
                    published_at = :publishedAt,
                    next_attempt_at = NULL,
                    locked_by = NULL,
                    locked_at = NULL,
                    last_error = NULL
                WHERE event_id = :eventId
                """)
                .bind("eventId", eventId)
                .bind("publishedAt", publishedAt)
                .fetch()
                .rowsUpdated()
                .then();
    }

    /**
     * Marks a failed publication attempt with retry backoff or terminal exhaustion.
     *
     * @param eventId event identifier
     * @param error safe error message
     * @param nextAttemptAt next retry timestamp; ignored for exhausted rows
     * @param exhausted whether the event has exceeded its retry budget
     * @param failedAt local failure timestamp
     * @return completion signal
     */
    public Mono<Void> markOutboxFailed(
            UUID eventId,
            String error,
            Instant nextAttemptAt,
            boolean exhausted,
            Instant failedAt
    ) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE profile_outbox
                SET status = :status,
                    attempts = attempts + 1,
                    last_error = :lastError,
                    next_attempt_at = :nextAttemptAt,
                    locked_by = NULL,
                    locked_at = NULL,
                    dead_lettered_at = :deadLetteredAt
                WHERE event_id = :eventId
                """)
                .bind("eventId", eventId)
                .bind("status", exhausted ? OutboxStatus.EXHAUSTED.name() : OutboxStatus.FAILED.name())
                .bind("lastError", truncate(error, 5000));
        spec = bindNullable(spec, "nextAttemptAt", exhausted ? null : nextAttemptAt, Instant.class);
        spec = bindNullable(spec, "deadLetteredAt", exhausted ? failedAt : null, Instant.class);
        return spec.fetch().rowsUpdated().then();
    }

    /**
     * Counts outbox rows by status for operational dashboards.
     *
     * @return stream of rows containing status and count
     */
    public Flux<OutboxStatusCountRow> countOutboxByStatus() {
        return databaseClient.sql("""
                SELECT status, COUNT(*) AS record_count
                FROM profile_outbox
                GROUP BY status
                ORDER BY status
                """)
                .map((row, metadata) -> new OutboxStatusCountRow(
                        OutboxStatus.valueOf(row.get("status", String.class)),
                        row.get("record_count", Long.class)
                ))
                .all();
    }

    /**
     * Inserts a consumed-event marker for idempotent projection processing.
     *
     * @param eventId source event identifier
     * @param profileId profile identifier
     * @param sourceRegion source region
     * @param consumedAt regional consumption timestamp
     * @return true when this event was not seen before
     */
    public Mono<Boolean> insertConsumedEvent(UUID eventId, UUID profileId, String sourceRegion, Instant consumedAt) {
        return databaseClient.sql("""
                INSERT INTO consumed_profile_events (event_id, profile_id, source_region, consumed_at)
                VALUES (:eventId, :profileId, :sourceRegion, :consumedAt)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .bind("eventId", eventId)
                .bind("profileId", profileId)
                .bind("sourceRegion", sourceRegion)
                .bind("consumedAt", consumedAt)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated == 1);
    }

    /**
     * Upserts the regional read model and ignores out-of-order older aggregate versions.
     *
     * @param row projected read-model row
     * @return true when the read model changed
     */
    public Mono<Boolean> upsertReadModel(ProfileReadModelRow row) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO profile_read_model
                    (profile_id, email, display_name, phone, status, version, source_region, event_id, event_time, replicated_at)
                VALUES
                    (:profileId, :email, :displayName, :phone, :status, :version, :sourceRegion, :eventId, :eventTime, :replicatedAt)
                ON CONFLICT (profile_id) DO UPDATE
                SET email = EXCLUDED.email,
                    display_name = EXCLUDED.display_name,
                    phone = EXCLUDED.phone,
                    status = EXCLUDED.status,
                    version = EXCLUDED.version,
                    source_region = EXCLUDED.source_region,
                    event_id = EXCLUDED.event_id,
                    event_time = EXCLUDED.event_time,
                    replicated_at = EXCLUDED.replicated_at
                WHERE profile_read_model.version < EXCLUDED.version
                """)
                .bind("profileId", row.profileId())
                .bind("email", row.email())
                .bind("displayName", row.displayName())
                .bind("status", row.status().name())
                .bind("version", row.version())
                .bind("sourceRegion", row.sourceRegion())
                .bind("eventId", row.eventId())
                .bind("eventTime", row.eventTime())
                .bind("replicatedAt", row.replicatedAt());
        spec = bindNullable(spec, "phone", row.phone(), String.class);
        return spec.fetch().rowsUpdated().map(updated -> updated == 1);
    }

    /**
     * Finds a regional profile read model by identifier.
     *
     * @param profileId profile identifier
     * @return read model row when present
     */
    public Mono<ProfileReadModelRow> findReadModel(UUID profileId) {
        return databaseClient.sql("""
                SELECT profile_id, email, display_name, phone, status, version, source_region,
                       event_id, event_time, replicated_at
                FROM profile_read_model
                WHERE profile_id = :profileId
                """)
                .bind("profileId", profileId)
                .map((row, metadata) -> mapReadModel(row))
                .one();
    }

    /**
     * Finds the newest event timestamp projected into the regional read model.
     *
     * @return newest event timestamp when the read model is not empty
     */
    public Mono<Instant> findLatestProjectedEventTime() {
        return databaseClient.sql("""
                SELECT event_time
                FROM profile_read_model
                ORDER BY event_time DESC
                LIMIT 1
                """)
                .map((row, metadata) -> toInstant(row, "event_time"))
                .one();
    }

    /**
     * Upserts the latest consumed Kafka offset for one region/topic/partition.
     *
     * @param topicName topic name
     * @param partitionId partition id
     * @param offset consumed offset
     * @param latestEventTime event timestamp
     * @param lastConsumedAt local consumption timestamp
     * @param region region name
     * @return completion signal
     */
    public Mono<Void> upsertProjectionWatermark(
            String topicName,
            int partitionId,
            long offset,
            Instant latestEventTime,
            Instant lastConsumedAt,
            String region
    ) {
        return databaseClient.sql("""
                INSERT INTO profile_projection_watermark
                    (topic_name, partition_id, current_offset, latest_event_time, last_consumed_at, region)
                VALUES
                    (:topicName, :partitionId, :currentOffset, :latestEventTime, :lastConsumedAt, :region)
                ON CONFLICT (topic_name, partition_id) DO UPDATE
                SET current_offset = GREATEST(profile_projection_watermark.current_offset, EXCLUDED.current_offset),
                    latest_event_time = CASE
                        WHEN EXCLUDED.current_offset >= profile_projection_watermark.current_offset THEN EXCLUDED.latest_event_time
                        ELSE profile_projection_watermark.latest_event_time
                    END,
                    last_consumed_at = CASE
                        WHEN EXCLUDED.current_offset >= profile_projection_watermark.current_offset THEN EXCLUDED.last_consumed_at
                        ELSE profile_projection_watermark.last_consumed_at
                    END,
                    region = EXCLUDED.region
                """)
                .bind("topicName", topicName)
                .bind("partitionId", partitionId)
                .bind("currentOffset", offset)
                .bind("latestEventTime", latestEventTime)
                .bind("lastConsumedAt", lastConsumedAt)
                .bind("region", region)
                .fetch()
                .rowsUpdated()
                .then();
    }

    /**
     * Lists Kafka projection watermarks for this regional read model.
     *
     * @return projection watermark rows
     */
    public Flux<ProjectionWatermarkRow> findProjectionWatermarks() {
        return databaseClient.sql("""
                SELECT topic_name, partition_id, current_offset, latest_event_time, last_consumed_at, region
                FROM profile_projection_watermark
                ORDER BY topic_name, partition_id
                """)
                .map((row, metadata) -> mapWatermark(row))
                .all();
    }

    /**
     * Finds the newest event timestamp in projection watermarks.
     *
     * @return newest watermark event timestamp
     */
    public Mono<Instant> findLatestWatermarkEventTime() {
        return databaseClient.sql("""
                SELECT latest_event_time
                FROM profile_projection_watermark
                ORDER BY latest_event_time DESC
                LIMIT 1
                """)
                .map((row, metadata) -> toInstant(row, "latest_event_time"))
                .one();
    }

    /**
     * Stores a malformed or unsupported Kafka payload so operators can inspect it without endless retries.
     *
     * @param row invalid event metadata and payload
     * @return completion signal
     */
    public Mono<Void> insertInvalidProfileEvent(InvalidProfileEventRow row) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO invalid_profile_events
                    (topic_name, partition_id, record_offset, record_key, payload, error, occurred_at, region)
                VALUES
                    (:topicName, :partitionId, :recordOffset, :recordKey, :payload, :error, :occurredAt, :region)
                ON CONFLICT (topic_name, partition_id, record_offset) DO NOTHING
                """)
                .bind("topicName", row.topicName())
                .bind("partitionId", row.partitionId())
                .bind("recordOffset", row.recordOffset())
                .bind("payload", truncate(row.payload(), 20000))
                .bind("error", truncate(row.error(), 5000))
                .bind("occurredAt", row.occurredAt())
                .bind("region", row.region());
        spec = bindNullable(spec, "recordKey", row.recordKey(), String.class);
        return spec.fetch().rowsUpdated().then();
    }

    /**
     * Lists recently quarantined invalid events.
     *
     * @param limit maximum rows to return
     * @return invalid event rows ordered by newest first
     */
    public Flux<InvalidProfileEventRow> findInvalidProfileEvents(int limit) {
        return databaseClient.sql("""
                SELECT id, topic_name, partition_id, record_offset, record_key, payload, error, occurred_at, region
                FROM invalid_profile_events
                ORDER BY occurred_at DESC
                LIMIT :limit
                """)
                .bind("limit", limit)
                .map((row, metadata) -> mapInvalidEvent(row))
                .all();
    }

    private ProfileRow mapProfile(Row row) {
        return new ProfileRow(
                row.get("id", UUID.class),
                row.get("email", String.class),
                row.get("display_name", String.class),
                row.get("phone", String.class),
                ProfileStatus.valueOf(row.get("status", String.class)),
                row.get("version", Long.class),
                toInstant(row, "created_at"),
                toInstant(row, "updated_at")
        );
    }

    private OutboxRow mapOutbox(Row row) {
        return new OutboxRow(
                row.get("event_id", UUID.class),
                row.get("aggregate_id", UUID.class),
                row.get("aggregate_version", Long.class),
                ProfileEventType.valueOf(row.get("event_type", String.class)),
                row.get("payload", String.class),
                OutboxStatus.valueOf(row.get("status", String.class)),
                row.get("attempts", Integer.class),
                row.get("last_error", String.class),
                toInstant(row, "created_at"),
                toInstant(row, "published_at"),
                toInstant(row, "next_attempt_at"),
                row.get("locked_by", String.class),
                toInstant(row, "locked_at"),
                toInstant(row, "dead_lettered_at")
        );
    }

    private ProfileReadModelRow mapReadModel(Row row) {
        return new ProfileReadModelRow(
                row.get("profile_id", UUID.class),
                row.get("email", String.class),
                row.get("display_name", String.class),
                row.get("phone", String.class),
                ProfileStatus.valueOf(row.get("status", String.class)),
                row.get("version", Long.class),
                row.get("source_region", String.class),
                row.get("event_id", UUID.class),
                toInstant(row, "event_time"),
                toInstant(row, "replicated_at")
        );
    }

    private ProjectionWatermarkRow mapWatermark(Row row) {
        return new ProjectionWatermarkRow(
                row.get("topic_name", String.class),
                row.get("partition_id", Integer.class),
                row.get("current_offset", Long.class),
                toInstant(row, "latest_event_time"),
                toInstant(row, "last_consumed_at"),
                row.get("region", String.class)
        );
    }

    private InvalidProfileEventRow mapInvalidEvent(Row row) {
        return new InvalidProfileEventRow(
                row.get("id", Long.class),
                row.get("topic_name", String.class),
                row.get("partition_id", Integer.class),
                row.get("record_offset", Long.class),
                row.get("record_key", String.class),
                row.get("payload", String.class),
                row.get("error", String.class),
                toInstant(row, "occurred_at"),
                row.get("region", String.class)
        );
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            Object value,
            Class<?> type
    ) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private Instant toInstant(Row row, String column) {
        Object value = row.get(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unsupported timestamp type for " + column + ": " + value.getClass().getName());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
