package ggee.alarmsender.notification.data

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationJpaRepository : JpaRepository<NotificationEntity, Long> {

    fun findByIdempotencyKey(idempotencyKey: String): NotificationEntity?

    fun findFirstByRecipientIdAndTypeAndRefTypeAndRefId(
        recipientId: String,
        type: ggee.alarmsender.notification.domain.NotificationType,
        refType: String?,
        refId: String?,
    ): NotificationEntity?
}
