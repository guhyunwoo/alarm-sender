package ggee.alarmsender.notification.teststub

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationType
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryNotificationRepository : NotificationRepository {
    private val store = ConcurrentHashMap<Long, Notification>()
    private val sequence = AtomicLong(0)

    override fun findById(id: Long): Notification? = store[id]

    override fun findByIdempotencyKey(key: String): Notification? =
        store.values.firstOrNull { it.idempotencyKey == key }

    override fun findByNaturalKey(
        recipientId: String,
        type: NotificationType,
        refType: String?,
        refId: String?,
    ): Notification? = store.values.firstOrNull {
        it.recipientId == recipientId &&
            it.type == type &&
            it.refType == refType &&
            it.refId == refId
    }

    override fun findByRecipient(
        recipientId: String,
        unreadOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<Notification> = store.values
        .asSequence()
        .filter { it.recipientId == recipientId }
        .filter { !unreadOnly || it.readAt == null }
        .sortedByDescending { it.createdAt }
        .drop(offset)
        .take(limit)
        .toList()

    override fun save(notification: Notification): Notification {
        val id = notification.id ?: sequence.incrementAndGet()
        val saved = notification.copy(id = id)
        store[id] = saved
        return saved
    }

    override fun update(notification: Notification): Notification {
        val id = notification.requireId()
        store[id] = notification
        return notification
    }

    override fun markAsReadIfUnread(id: Long, readAt: Instant): Boolean {
        var updated = false
        store.compute(id) { _, current ->
            if (current == null || current.readAt != null) {
                current
            } else {
                updated = true
                current.markAsRead(readAt)
            }
        }
        return updated
    }

    fun clear() {
        store.clear()
        sequence.set(0)
    }

    fun all(): List<Notification> = store.values.toList()
}
