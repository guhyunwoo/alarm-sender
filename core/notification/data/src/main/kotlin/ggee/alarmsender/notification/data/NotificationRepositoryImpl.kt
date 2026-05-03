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

    override fun markAsReadIfUnread(id: Long, readAt: Instant): Boolean =
        jdbcTemplate.update(
            """
                UPDATE notification
                SET read_at = ?
                WHERE id = ?
                  AND read_at IS NULL
            """.trimIndent(),
            Timestamp.from(readAt),
            id,
        ) == 1
}
