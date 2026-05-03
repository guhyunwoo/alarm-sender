package ggee.alarmsender.notification.domain

import java.time.Instant

/**
 * 알림 본체. 상태 전이는 이 객체가 직접 한다.
 *
 * 멱등성 키:
 *  - idempotencyKey  → 클라이언트 재시도 방어 (UNIQUE)
 *  - (recipientId, type, refType, refId) → 자연 키 (UNIQUE)
 */
data class Notification(
    val id: Long? = null,
    val recipientId: String,
    val type: NotificationType,
    val channel: NotificationChannel,
    val payload: Map<String, Any?>,
    val idempotencyKey: String?,
    val refType: String?,
    val refId: String?,
    val status: NotificationStatus,
    val readAt: Instant? = null,
    val createdAt: Instant,
    val scheduledAt: Instant? = null,
    val sentAt: Instant? = null,
) {
    /**
     * 객체 단위 멱등성. 이미 readAt 이 있으면 그대로, 없으면 now 로 set.
     * 동시 호출 race 까지 막는 건 저장소의 조건부 UPDATE 책임이다.
     */
    fun markAsRead(now: Instant): Notification =
        if (readAt != null) this else copy(readAt = now)

    fun markSent(now: Instant): Notification {
        require(status == NotificationStatus.PENDING) {
            "PENDING 만 SENT 로 전이 가능 (현재: $status)"
        }
        return copy(status = NotificationStatus.SENT, sentAt = now)
    }

    fun markDeadLetter(): Notification = copy(status = NotificationStatus.DEAD_LETTER)

    fun resetForManualRetry(): Notification {
        // 상태 전제 위반은 IllegalStateException — '지금 상태에서는 못 한다' 라는 뜻.
        // 핸들러에서 409 Conflict 로 매핑된다 (재시도 요청 자체는 valid).
        check(status == NotificationStatus.DEAD_LETTER) {
            "수동 재시도는 DEAD_LETTER 알림에만 가능 (현재: $status)"
        }
        return copy(status = NotificationStatus.PENDING)
    }

    fun requireId(): Long = id ?: error("아직 영속화되지 않은 Notification")

    companion object {
        fun create(
            recipientId: String,
            type: NotificationType,
            channel: NotificationChannel,
            payload: Map<String, Any?>,
            idempotencyKey: String?,
            refType: String?,
            refId: String?,
            now: Instant,
            scheduledAt: Instant? = null,
        ): Notification = Notification(
            recipientId = recipientId,
            type = type,
            channel = channel,
            payload = payload,
            idempotencyKey = idempotencyKey,
            refType = refType,
            refId = refId,
            status = NotificationStatus.PENDING,
            createdAt = now,
            scheduledAt = scheduledAt,
        )
    }
}
