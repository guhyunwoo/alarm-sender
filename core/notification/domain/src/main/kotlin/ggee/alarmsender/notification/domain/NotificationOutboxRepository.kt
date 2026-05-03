package ggee.alarmsender.notification.domain

import java.time.Duration
import java.time.Instant

interface NotificationOutboxRepository {
    fun save(outbox: NotificationOutbox): NotificationOutbox

    fun findById(id: Long): NotificationOutbox?

    fun findByNotificationId(notificationId: Long): NotificationOutbox?

    /**
     * SKIP LOCKED 기반 안전 폴링 + claim. PENDING row 를 IN_PROGRESS 로 전이시키며 잠근다.
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
