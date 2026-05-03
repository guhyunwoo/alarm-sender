package ggee.alarmsender.notification.teststub

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationTemplate
import ggee.alarmsender.notification.domain.NotificationTemplateRepository
import ggee.alarmsender.notification.domain.NotificationType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryNotificationTemplateRepository : NotificationTemplateRepository {
    private val store = ConcurrentHashMap<Long, NotificationTemplate>()
    private val sequence = AtomicLong(0)

    override fun findAll(): List<NotificationTemplate> =
        store.values.sortedWith(compareBy<NotificationTemplate> { it.type.name }.thenBy { it.channel.name })

    override fun findByTypeAndChannel(type: NotificationType, channel: NotificationChannel): NotificationTemplate? =
        store.values.firstOrNull { it.type == type && it.channel == channel }

    override fun save(template: NotificationTemplate): NotificationTemplate {
        val existingId = findByTypeAndChannel(template.type, template.channel)?.id
        val id = template.id ?: existingId ?: sequence.incrementAndGet()
        val saved = template.copy(id = id)
        store[id] = saved
        return saved
    }

    fun clear() {
        store.clear()
        sequence.set(0)
    }
}
