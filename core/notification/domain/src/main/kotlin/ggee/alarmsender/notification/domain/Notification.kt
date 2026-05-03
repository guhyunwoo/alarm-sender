package ggee.alarmsender.notification.domain

import java.time.Instant

/**
 * 알림 본체. 상태 전이 책임을 도메인 객체가 직접 갖는다.
 *
 * 멱등성 보장 키:
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
    val sentAt: Instant? = null,
) {
    /**
     * 멀티 디바이스 동시 read 시에도 한 번만 set, 이후 호출은 no-op.
     */
    fun markAsRead(now: Instant): Notification =
        if (readAt != null) this else copy(readAt = now)

    fun markSent(now: Instant): Notification {
        require(status == NotificationStatus.PENDING) {
            "${'$'}status 상태에서 SENT 로 전이할 수 없다"
        }
        return copy(status = NotificationStatus.SENT, sentAt = now)
    }

    fun markDeadLetter(): Notification = copy(status = NotificationStatus.DEAD_LETTER)

    fun resetForManualRetry(): Notification {
        // 도메인 상태 전제 위반은 IllegalStateException — '현재 상태에서 동작 불가' 의미.
        // 핸들러에서 409 Conflict 로 매핑된다 (재시도 입력 자체는 valid).
        check(status == NotificationStatus.DEAD_LETTER) {
            "DEAD_LETTER 가 아닌 상태에서 수동 재시도 불가 (현재: ${'$'}status)"
        }
        return copy(status = NotificationStatus.PENDING)
    }

    fun requireId(): Long = id ?: error("영속화되지 않은 Notification")

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
        )
    }
}
