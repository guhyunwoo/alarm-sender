package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.NotificationTemplate
import ggee.alarmsender.notification.domain.NotificationType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class CreateNotificationRequest(
    @field:NotBlank
    val recipientId: String,
    @field:NotNull
    val type: NotificationType,
    @field:NotNull
    val channel: NotificationChannel,
    val payload: Map<String, Any?> = emptyMap(),
    val refType: String? = null,
    val refId: String? = null,
    val scheduledAt: Instant? = null,
)

data class NotificationResponse(
    val id: Long,
    val recipientId: String,
    val type: NotificationType,
    val channel: NotificationChannel,
    val payload: Map<String, Any?>,
    val refType: String?,
    val refId: String?,
    val status: NotificationStatus,
    val readAt: Instant?,
    val createdAt: Instant,
    val scheduledAt: Instant?,
    val sentAt: Instant?,
) {
    companion object {
        fun from(n: Notification): NotificationResponse = NotificationResponse(
            id = n.requireId(),
            recipientId = n.recipientId,
            type = n.type,
            channel = n.channel,
            payload = n.payload,
            refType = n.refType,
            refId = n.refId,
            status = n.status,
            readAt = n.readAt,
            createdAt = n.createdAt,
            scheduledAt = n.scheduledAt,
            sentAt = n.sentAt,
        )
    }
}

data class ReadNotificationResponse(
    val id: Long,
    val readAt: Instant,
    /** 이번 호출이 처음으로 readAt 을 set 했는가. */
    val newlyRead: Boolean,
)

data class ErrorResponse(
    val code: String,
    val message: String,
)

data class NotificationTemplateResponse(
    val id: Long,
    val type: NotificationType,
    val channel: NotificationChannel,
    val subjectTemplate: String,
    val bodyTemplate: String,
    val updatedAt: Instant,
) {
    companion object {
        fun from(template: NotificationTemplate): NotificationTemplateResponse = NotificationTemplateResponse(
            id = template.requireId(),
            type = template.type,
            channel = template.channel,
            subjectTemplate = template.subjectTemplate,
            bodyTemplate = template.bodyTemplate,
            updatedAt = template.updatedAt,
        )
    }
}

data class UpdateNotificationTemplateRequest(
    @field:NotBlank
    val subjectTemplate: String,
    @field:NotBlank
    val bodyTemplate: String,
)
