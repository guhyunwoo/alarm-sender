package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationType
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper

@Repository
class NotificationRepositoryImpl(
    private val jpa: NotificationJpaRepository,
    private val objectMapper: ObjectMapper,
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
    ): List<Notification> {
        // Phase 1 에서는 단순 조회만 구현. recipient/unread 인덱스를 활용한 최적 쿼리는 후속에서 추가.
        return emptyList()
    }

    override fun save(notification: Notification): Notification {
        val saved = jpa.save(notification.toEntity(objectMapper))
        return saved.toDomain(objectMapper)
    }

    override fun update(notification: Notification): Notification {
        notification.requireId()
        val saved = jpa.save(notification.toEntity(objectMapper))
        return saved.toDomain(objectMapper)
    }
}
