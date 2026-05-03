package ggee.alarmsender.notification.domain

interface NotificationTemplateRepository {
    fun findAll(): List<NotificationTemplate>

    fun findByTypeAndChannel(type: NotificationType, channel: NotificationChannel): NotificationTemplate?

    fun save(template: NotificationTemplate): NotificationTemplate
}
