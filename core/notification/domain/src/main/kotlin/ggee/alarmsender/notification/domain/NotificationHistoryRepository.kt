package ggee.alarmsender.notification.domain

interface NotificationHistoryRepository {
    fun append(history: NotificationHistory): NotificationHistory

    fun findByNotificationId(notificationId: Long): List<NotificationHistory>
}
