package ggee.alarmsender.notification.usecase.sendnotification

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationType
import java.time.Instant

interface SendNotificationUseCase {
    fun execute(command: SendNotificationCommand): SendNotificationResult
}

data class SendNotificationCommand(
    val recipientId: String,
    val type: NotificationType,
    val channel: NotificationChannel,
    val payload: Map<String, Any?>,
    val idempotencyKey: String?,
    val refType: String?,
    val refId: String?,
    val scheduledAt: Instant?,
)

data class SendNotificationResult(
    val notification: Notification,
    /**
     * 멱등성 키 또는 자연 키로 이미 존재하던 알림이라 재사용된 경우 true.
     * 새로 적재된 경우 false.
     */
    val deduplicated: Boolean,
)
