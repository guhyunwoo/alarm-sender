package ggee.alarmsender.notification.testfixture

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.NotificationTemplate
import ggee.alarmsender.notification.domain.NotificationType
import ggee.alarmsender.notification.domain.OutboxStatus
import java.time.Instant

object NotificationFixtures {

    fun notification(
        id: Long? = null,
        recipientId: String = "user-1",
        type: NotificationType = NotificationType.ENROLL_COMPLETED,
        channel: NotificationChannel = NotificationChannel.EMAIL,
        payload: Map<String, Any?> = mapOf("title" to "수강 신청 완료"),
        idempotencyKey: String? = "idem-1",
        refType: String? = "ENROLLMENT",
        refId: String? = "100",
        status: NotificationStatus = NotificationStatus.PENDING,
        readAt: Instant? = null,
        createdAt: Instant = Instant.parse("2026-05-02T10:00:00Z"),
        scheduledAt: Instant? = null,
        sentAt: Instant? = null,
    ): Notification = Notification(
        id = id,
        recipientId = recipientId,
        type = type,
        channel = channel,
        payload = payload,
        idempotencyKey = idempotencyKey,
        refType = refType,
        refId = refId,
        status = status,
        readAt = readAt,
        createdAt = createdAt,
        scheduledAt = scheduledAt,
        sentAt = sentAt,
    )

    fun template(
        id: Long? = null,
        type: NotificationType = NotificationType.ENROLL_COMPLETED,
        channel: NotificationChannel = NotificationChannel.EMAIL,
        subjectTemplate: String = "수강 신청 완료: {{course_name}}",
        bodyTemplate: String = "{{recipient_name}}님, {{course_name}} 수강 신청이 완료되었습니다.",
        updatedAt: Instant = Instant.parse("2026-05-02T10:00:00Z"),
    ): NotificationTemplate = NotificationTemplate(
        id = id,
        type = type,
        channel = channel,
        subjectTemplate = subjectTemplate,
        bodyTemplate = bodyTemplate,
        updatedAt = updatedAt,
    )

    fun outbox(
        id: Long? = null,
        notificationId: Long = 100L,
        status: OutboxStatus = OutboxStatus.PENDING,
        availableAt: Instant = Instant.parse("2026-05-02T10:00:00Z"),
        attemptCount: Int = 0,
        leaseOwner: String? = null,
        leaseExpiresAt: Instant? = null,
        lastError: String? = null,
        createdAt: Instant = Instant.parse("2026-05-02T10:00:00Z"),
        updatedAt: Instant = Instant.parse("2026-05-02T10:00:00Z"),
    ): NotificationOutbox = NotificationOutbox(
        id = id,
        notificationId = notificationId,
        status = status,
        availableAt = availableAt,
        attemptCount = attemptCount,
        leaseOwner = leaseOwner,
        leaseExpiresAt = leaseExpiresAt,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
