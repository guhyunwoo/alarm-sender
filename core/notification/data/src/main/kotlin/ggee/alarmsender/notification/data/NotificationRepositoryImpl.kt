package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant

@Repository
class NotificationRepositoryImpl(
    private val jpa: NotificationJpaRepository,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: JdbcTemplate,
) : NotificationRepository {

    override fun findById(id: Long): Notification? =
        jpa.findById(id).orElse(null)?.toDomain(objectMapper)

    override fun findByIdempotencyKey(key: String): Notification? =
        jpa.findByIdempotencyKey(key)?.toDomain(objectMapper)

    override fun findByNaturalKey(
        recipientId: String,
        type: NotificationType,
        refType: String?,
        refId: String?,
    ): Notification? = jpa.findFirstByRecipientIdAndTypeAndRefTypeAndRefId(
        recipientId, type, refType, refId,
    )?.toDomain(objectMapper)

    override fun findByRecipient(
        recipientId: String,
        unreadOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<Notification> = jpa.findByRecipientPage(recipientId, unreadOnly, limit, offset)
        .map { it.toDomain(objectMapper) }

    override fun save(notification: Notification): Notification {
        val saved = jpa.save(notification.toEntity(objectMapper))
        return saved.toDomain(objectMapper)
    }

    override fun update(notification: Notification): Notification {
        notification.requireId()
        val saved = jpa.save(notification.toEntity(objectMapper))
        return saved.toDomain(objectMapper)
    }

    /**
     * CTE 로 한 번의 RT 안에 (a) 조건부 UPDATE 시도, (b) effective `read_at` 조회를 모두 수행.
     * COALESCE 가 우리가 방금 set 한 값과 기존 값 중 살아있는 쪽을 돌려준다.
     * 호출자가 JPA 1차 캐시로 재조회하다 stale `read_at = NULL` 을 볼 위험이 없다.
     */
    override fun markAsReadIfUnread(id: Long, readAt: Instant): Instant? {
        val sql = """
            WITH updated AS (
                UPDATE notification
                SET read_at = ?
                WHERE id = ?
                  AND read_at IS NULL
                RETURNING read_at
            )
            SELECT COALESCE((SELECT read_at FROM updated), n.read_at) AS effective_read_at
            FROM notification n
            WHERE n.id = ?
        """.trimIndent()
        val results = jdbcTemplate.query(
            sql,
            { rs, _ -> rs.getTimestamp("effective_read_at")?.toInstant() },
            Timestamp.from(readAt), id, id,
        )
        return results.firstOrNull()
    }
}
