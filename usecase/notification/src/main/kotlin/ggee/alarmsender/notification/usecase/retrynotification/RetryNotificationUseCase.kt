package ggee.alarmsender.notification.usecase.retrynotification

import ggee.alarmsender.notification.domain.Notification

interface RetryNotificationUseCase {
    /**
     * DEAD_LETTER 상태의 알림을 운영자(또는 본인) 의지로 재시도 큐(PENDING) 로 복귀시킨다.
     *
     * 정책:
     *  - DEAD_LETTER 가 아닌 알림에 대해서는 [IllegalStateException] 발생
     *  - attempt_count = 0 으로 리셋 (운영자의 명시적 의지로 새 시도를 시작한다는 의미)
     *  - history 에 reason=MANUAL_RETRY 적재 — 자동 재시도와 구분 가능
     *
     * @throws ggee.alarmsender.notification.domain.exception.NotificationNotFoundException 알림이 존재하지 않을 때
     * @throws ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException 본인이 아닌 사용자가 호출할 때
     */
    fun execute(command: RetryNotificationCommand): Notification
}

data class RetryNotificationCommand(
    val notificationId: Long,
    val requesterId: String,
)
