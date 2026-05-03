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

    /**
     * 동시 read 시 첫 호출만 성공시키는 저장소 경계 조건부 UPDATE.
     *
     * @return read_at 이 NULL 이던 row 를 이번 호출이 채웠으면 true, 이미 읽음이면 false.
     */
    fun markAsReadIfUnread(id: Long, readAt: java.time.Instant): Boolean
}
