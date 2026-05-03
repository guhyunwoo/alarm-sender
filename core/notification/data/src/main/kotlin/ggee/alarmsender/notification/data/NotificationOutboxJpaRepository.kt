package ggee.alarmsender.notification.data

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationOutboxJpaRepository : JpaRepository<NotificationOutboxEntity, Long> {

    fun findByNotificationId(notificationId: Long): NotificationOutboxEntity?
}
