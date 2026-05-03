package ggee.alarmsender.notification.domain

import java.time.Duration
import java.time.Instant

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

interface NotificationOutboxRepository {
    fun save(outbox: NotificationOutbox): NotificationOutbox

    fun findById(id: Long): NotificationOutbox?

    fun findByNotificationId(notificationId: Long): NotificationOutbox?

    /**
     * SKIP LOCKED 기반의 안전한 폴링 + claim. 트랜잭션 내에서 호출되어야 한다.
     *
     * @return 잠금에 성공해 IN_PROGRESS 로 전이된 row 들의 도메인 표현
     */
    fun claimBatch(
        workerId: String,
        now: Instant,
        leaseDuration: Duration,
        limit: Int,
    ): List<NotificationOutbox>

    /**
     * lease 가 만료된 IN_PROGRESS row 조회. 배치가 reclaim 대상으로 사용한다.
     */
    fun findExpired(now: Instant, limit: Int): List<NotificationOutbox>

    fun update(outbox: NotificationOutbox): NotificationOutbox
}

interface NotificationHistoryRepository {
    fun append(history: NotificationHistory): NotificationHistory

    fun findByNotificationId(notificationId: Long): List<NotificationHistory>
}
