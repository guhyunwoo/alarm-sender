package ggee.alarmsender.notification.domain

interface NotificationRepository {
    fun findById(id: Long): Notification?

    fun findByIdempotencyKey(key: String): Notification?

    fun findByNaturalKey(
        recipientId: String,
        type: NotificationType,
        refType: String?,
        refId: String?,
    ): Notification?

    fun findByRecipient(
        recipientId: String,
        unreadOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<Notification>

    fun save(notification: Notification): Notification

    fun update(notification: Notification): Notification
}
