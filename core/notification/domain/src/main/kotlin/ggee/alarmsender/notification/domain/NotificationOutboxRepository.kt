package ggee.alarmsender.notification.domain

import java.time.Duration
import java.time.Instant

interface NotificationOutboxRepository {
    fun save(outbox: NotificationOutbox): NotificationOutbox

    fun findById(id: Long): NotificationOutbox?

    fun findByNotificationId(notificationId: Long): NotificationOutbox?

    /**
     * SKIP LOCKED 폴링으로 PENDING row 를 잠그고 IN_PROGRESS 로 전이시킨다.
     *
     * @return 잠금 성공해 IN_PROGRESS 로 넘어간 row 들
     */
    fun claimBatch(
        workerId: String,
        now: Instant,
        leaseDuration: Duration,
        limit: Int,
    ): List<NotificationOutbox>

    /**
     * lease 만료된 IN_PROGRESS row. 배치가 reclaim 대상으로 쓴다.
     */
    fun findExpired(now: Instant, limit: Int): List<NotificationOutbox>

    fun update(outbox: NotificationOutbox): NotificationOutbox
}
