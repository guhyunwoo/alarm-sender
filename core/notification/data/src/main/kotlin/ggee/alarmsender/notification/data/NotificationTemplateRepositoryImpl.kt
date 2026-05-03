package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationTemplate
import ggee.alarmsender.notification.domain.NotificationTemplateRepository
import ggee.alarmsender.notification.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface NotificationTemplateJpaRepository : JpaRepository<NotificationTemplateEntity, Long> {
    fun findByTypeAndChannel(type: NotificationType, channel: NotificationChannel): NotificationTemplateEntity?
}

@Repository
class NotificationTemplateRepositoryImpl(
    private val jpa: NotificationTemplateJpaRepository,
) : NotificationTemplateRepository {

    override fun findAll(): List<NotificationTemplate> =
        jpa.findAll().map { it.toDomain() }

    override fun findByTypeAndChannel(type: NotificationType, channel: NotificationChannel): NotificationTemplate? =
        jpa.findByTypeAndChannel(type, channel)?.toDomain()

    override fun save(template: NotificationTemplate): NotificationTemplate =
        jpa.save(template.toEntity()).toDomain()
}
