package ggee.alarmsender.notification.teststub

import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.domain.OutboxStatus
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 단일 스레드 테스트용 in-memory 구현. SKIP LOCKED 의 동시성 의미는 흉내 내지 않으며,
 * 다중 워커 동시성 테스트는 TestContainers 통합 테스트에서 커버한다.
 */
class InMemoryNotificationOutboxRepository : NotificationOutboxRepository {
    private val store = ConcurrentHashMap<Long, NotificationOutbox>()
    private val sequence = AtomicLong(0)

    override fun save(outbox: NotificationOutbox): NotificationOutbox {
        val id = outbox.id ?: sequence.incrementAndGet()
        val saved = outbox.copy(id = id)
        store[id] = saved
        return saved
    }

    override fun findById(id: Long): NotificationOutbox? = store[id]

    override fun findByNotificationId(notificationId: Long): NotificationOutbox? =
        store.values.firstOrNull { it.notificationId == notificationId }

    @Synchronized
    override fun claimBatch(
        workerId: String,
        now: Instant,
        leaseDuration: Duration,
        limit: Int,
    ): List<NotificationOutbox> {
        val candidates = store.values
            .asSequence()
            .filter { it.status == OutboxStatus.PENDING }
            .filter { !it.availableAt.isAfter(now) }
            .sortedBy { it.availableAt }
            .take(limit)
            .toList()

        return candidates.map { candidate ->
            val claimed = candidate.claim(workerId, now, leaseDuration)
            store[claimed.requireId()] = claimed
            claimed
        }
    }

    override fun findExpired(now: Instant, limit: Int): List<NotificationOutbox> =
        store.values
            .asSequence()
            .filter { it.status == OutboxStatus.IN_PROGRESS }
            .filter { it.leaseExpiresAt?.isBefore(now) == true }
            .take(limit)
            .toList()

    override fun update(outbox: NotificationOutbox): NotificationOutbox {
        val id = outbox.requireId()
        store[id] = outbox
        return outbox
    }

    fun clear() {
        store.clear()
        sequence.set(0)
    }

    fun all(): List<NotificationOutbox> = store.values.toList()
}
