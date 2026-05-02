package ggee.alarmsender.notification.teststub

import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryNotificationHistoryRepository : NotificationHistoryRepository {
    private val store = ConcurrentHashMap<Long, NotificationHistory>()
    private val sequence = AtomicLong(0)

    override fun append(history: NotificationHistory): NotificationHistory {
        val id = sequence.incrementAndGet()
        val saved = history.copy(id = id)
        store[id] = saved
        return saved
    }

    override fun findByNotificationId(notificationId: Long): List<NotificationHistory> =
        store.values
            .filter { it.notificationId == notificationId }
            .sortedBy { it.occurredAt }

    fun clear() {
        store.clear()
        sequence.set(0)
    }

    fun all(): List<NotificationHistory> = store.values.toList()
}
