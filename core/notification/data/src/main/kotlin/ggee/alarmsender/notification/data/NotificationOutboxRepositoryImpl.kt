package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
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

    override fun update(outbox: NotificationOutbox): NotificationOutbox {
        outbox.requireId()
        return jpa.save(outbox.toEntity()).toDomain()
    }

    companion object {
        private val OUTBOX_ROW_MAPPER = RowMapper { rs, _ ->
            NotificationOutbox(
                id = rs.getLong("id"),
                notificationId = rs.getLong("notification_id"),
                status = ggee.alarmsender.notification.domain.OutboxStatus.valueOf(rs.getString("status")),
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
