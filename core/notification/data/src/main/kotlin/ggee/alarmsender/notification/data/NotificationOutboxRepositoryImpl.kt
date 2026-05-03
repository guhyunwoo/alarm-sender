package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.domain.OutboxStatus
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@Repository
class NotificationOutboxRepositoryImpl(
    private val jpa: NotificationOutboxJpaRepository,
    private val jdbc: NamedParameterJdbcTemplate,
) : NotificationOutboxRepository {

    override fun save(outbox: NotificationOutbox): NotificationOutbox =
        jpa.save(outbox.toEntity()).toDomain()

    override fun findById(id: Long): NotificationOutbox? =
        jpa.findById(id).orElse(null)?.toDomain()

    override fun findByNotificationId(notificationId: Long): NotificationOutbox? =
        jpa.findByNotificationId(notificationId)?.toDomain()

    /**
     * PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` + `UPDATE ... RETURNING *` 1-RT 패턴.
     *
     * `IN (SELECT ... FOR UPDATE SKIP LOCKED)` 서브쿼리가 후보 row 들을 잠그고,
     * 외부 UPDATE 가 `IN_PROGRESS` 로 전이시키며 RETURNING 으로 갱신된 row 를 그대로 반환한다.
     * 다중 워커가 동시에 호출해도 같은 row 를 두 번 잡지 않는다.
     */
    override fun claimBatch(
        workerId: String,
        now: Instant,
        leaseDuration: Duration,
        limit: Int,
    ): List<NotificationOutbox> {
        val sql = """
            UPDATE notification_outbox
            SET status = 'IN_PROGRESS',
                lease_owner = :owner,
                lease_expires_at = :leaseExpiresAt,
                updated_at = :now,
                version = version + 1
            WHERE id IN (
                SELECT id FROM notification_outbox
                WHERE status = 'PENDING' AND available_at <= :now
                ORDER BY available_at ASC, id ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id, notification_id, status, available_at, attempt_count,
                      lease_owner, lease_expires_at, last_error, created_at, updated_at, version
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("owner", workerId)
            .addValue("leaseExpiresAt", Timestamp.from(now.plus(leaseDuration)))
            .addValue("now", Timestamp.from(now))
            .addValue("limit", limit)

        return jdbc.query(sql, params, OUTBOX_ROW_MAPPER)
    }

    override fun findExpired(now: Instant, limit: Int): List<NotificationOutbox> {
        val sql = """
            SELECT id, notification_id, status, available_at, attempt_count,
                   lease_owner, lease_expires_at, last_error, created_at, updated_at, version
            FROM notification_outbox
            WHERE status = 'IN_PROGRESS'
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at < :now
            ORDER BY lease_expires_at ASC
            LIMIT :limit
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("now", Timestamp.from(now))
            .addValue("limit", limit)

        return jdbc.query(sql, params, OUTBOX_ROW_MAPPER)
    }

    /**
     * `WHERE id=? AND version=?` 단일 SQL 로 stale write 차단.
     * `claimBatch` 와 동일하게 raw UPDATE … RETURNING * 로 1-RT.
     * 영향 row=0 이면 `OptimisticLockingFailureException` 으로 워커 try/catch 가 받아 row 격리한다.
     */
    override fun update(outbox: NotificationOutbox): NotificationOutbox {
        val id = outbox.requireId()
        val sql = """
            UPDATE notification_outbox
            SET status = :status,
                available_at = :availableAt,
                attempt_count = :attemptCount,
                lease_owner = :leaseOwner,
                lease_expires_at = :leaseExpiresAt,
                last_error = :lastError,
                updated_at = :updatedAt,
                version = version + 1
            WHERE id = :id AND version = :expectedVersion
            RETURNING id, notification_id, status, available_at, attempt_count,
                      lease_owner, lease_expires_at, last_error, created_at, updated_at, version
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("expectedVersion", outbox.version)
            .addValue("status", outbox.status.name)
            .addValue("availableAt", Timestamp.from(outbox.availableAt))
            .addValue("attemptCount", outbox.attemptCount)
            .addValue("leaseOwner", outbox.leaseOwner)
            .addValue("leaseExpiresAt", outbox.leaseExpiresAt?.let { Timestamp.from(it) })
            .addValue("lastError", outbox.lastError)
            .addValue("updatedAt", Timestamp.from(outbox.updatedAt))

        return jdbc.query(sql, params, OUTBOX_ROW_MAPPER).firstOrNull()
            ?: throw OptimisticLockingFailureException(
                "NotificationOutbox(id=$id, version=${outbox.version}) — 다른 트랜잭션이 먼저 갱신함"
            )
    }

    companion object {
        private val OUTBOX_ROW_MAPPER = RowMapper { rs, _ ->
            NotificationOutbox(
                id = rs.getLong("id"),
                notificationId = rs.getLong("notification_id"),
                status = OutboxStatus.valueOf(rs.getString("status")),
                availableAt = rs.getTimestamp("available_at").toInstant(),
                attemptCount = rs.getInt("attempt_count"),
                leaseOwner = rs.getString("lease_owner"),
                leaseExpiresAt = rs.getTimestamp("lease_expires_at")?.toInstant(),
                lastError = rs.getString("last_error"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
                version = rs.getLong("version"),
            )
        }
    }
}
