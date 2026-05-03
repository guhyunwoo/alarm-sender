package ggee.alarmsender.notification.usecase.retrynotification

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.RequesterRole

interface RetryNotificationUseCase {
    /**
     * DEAD_LETTER 상태의 알림을 운영자 의지로 재시도 큐(PENDING) 로 복귀시킨다.
     *
     * 권한:
     *  - **OPERATOR 만 호출 가능.** 본인 알림이라도 일반 USER 는 직접 재시도할 수 없다.
     *    이유: DEAD_LETTER 는 시스템 상태이며 무분별한 재시도는 동일 영구실패 패턴을
     *    반복하거나 외부 SMTP 차단을 가속할 수 있어 운영 판단이 필요하다.
     *
     * 정책:
     *  - DEAD_LETTER 가 아닌 알림에 대해서는 [IllegalStateException] 발생 (도메인 상태 전제)
     *  - attempt_count = 0 으로 리셋 (운영자의 명시적 의지로 새 시도를 시작한다는 의미)
     *  - history 에 reason=MANUAL_RETRY 적재 — 자동 재시도와 구분 가능
     *
     * @throws ggee.alarmsender.notification.domain.exception.NotificationNotFoundException 알림이 존재하지 않을 때
     * @throws ggee.alarmsender.notification.domain.exception.OperatorOnlyException 호출자가 OPERATOR 가 아닐 때
     */
    fun execute(command: RetryNotificationCommand): Notification
}

data class RetryNotificationCommand(
    val notificationId: Long,
    val requesterId: String,
    val requesterRole: RequesterRole,
)
