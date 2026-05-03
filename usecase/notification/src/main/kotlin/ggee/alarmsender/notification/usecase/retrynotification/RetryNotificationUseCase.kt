package ggee.alarmsender.notification.usecase.retrynotification

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.RequesterRole
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.domain.exception.OperatorOnlyException

interface RetryNotificationUseCase {
    /**
     * DEAD_LETTER 알림을 운영자가 직접 재시도 큐(PENDING) 로 되돌린다.
     *
     * 권한:
     *  - **OPERATOR 만 호출 가능.** 본인 알림이라도 일반 USER 는 직접 재시도할 수 없다.
     *    이유: DEAD_LETTER 는 시스템 상태이고, 무분별한 재시도는 같은 영구 실패를 반복하거나
     *    외부 SMTP 차단 위험을 키울 수 있어서 운영 판단이 필요하다.
     *
     * 정책:
     *  - DEAD_LETTER 가 아닌 알림에 호출하면 [IllegalStateException] (상태 전제 위반)
     *  - attempt_count = 0 으로 리셋 (운영자가 새로 시도를 거는 것으로 본다)
     *  - history 에 reason=MANUAL_RETRY 적재 — 자동 재시도와 구분된다
     *
     * @throws NotificationNotFoundException 알림이 없을 때
     * @throws OperatorOnlyException 호출자가 OPERATOR 가 아닐 때
     */
    fun execute(command: RetryNotificationCommand): Notification
}

data class RetryNotificationCommand(
    val notificationId: Long,
    val requesterId: String,
    val requesterRole: RequesterRole,
)
