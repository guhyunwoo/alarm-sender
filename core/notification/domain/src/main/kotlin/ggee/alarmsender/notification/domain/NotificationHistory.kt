package ggee.alarmsender.notification.domain

import java.time.Instant

/**
 * 상태 전이 이력. 감사·디버깅 목적의 append-only 기록.
 */
data class NotificationHistory(
    val id: Long? = null,
    val notificationId: Long,
    val fromStatus: NotificationStatus?,
    val toStatus: NotificationStatus,
    val reason: HistoryReason,
    val detail: String? = null,
    val occurredAt: Instant,
) {
    companion object {
        fun of(
            notificationId: Long,
            from: NotificationStatus?,
            to: NotificationStatus,
            reason: HistoryReason,
            now: Instant,
            detail: String? = null,
        ): NotificationHistory = NotificationHistory(
            notificationId = notificationId,
            fromStatus = from,
            toStatus = to,
            reason = reason,
            detail = detail,
            occurredAt = now,
        )
    }
}
