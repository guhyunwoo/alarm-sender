package ggee.alarmsender.notification.usecase.readnotification.port

import ggee.alarmsender.notification.domain.Notification

interface ReadNotificationUseCase {
    fun execute(command: ReadNotificationCommand): ReadNotificationResult
}

data class ReadNotificationCommand(
    val notificationId: Long,
    val requesterId: String,
)

data class ReadNotificationResult(
    val notification: Notification,
    /** 이번 호출이 처음으로 readAt 을 set 했는가. 이미 읽힌 알림이면 false. */
    val newlyRead: Boolean,
)
