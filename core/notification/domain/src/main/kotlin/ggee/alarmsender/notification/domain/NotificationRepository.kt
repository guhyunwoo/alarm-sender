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
     * 동시 읽음 처리에서 최초 1건만 성공해야 하므로 저장소 경계에서 조건부 갱신한다.
     *
     * @return readAt 이 null 이던 row 를 이번 호출이 갱신했으면 true, 이미 읽음 상태면 false.
     */
    fun markAsReadIfUnread(id: Long, readAt: java.time.Instant): Boolean
}
