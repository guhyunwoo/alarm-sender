package ggee.alarmsender.notification.usecase.retrynotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.RequesterRole
import ggee.alarmsender.notification.domain.exception.NotificationDataInconsistencyException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.domain.exception.OperatorOnlyException
import ggee.alarmsender.notification.usecase.dispatchnotification.OutboxPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 운영자(OPERATOR) 가 DEAD_LETTER 격리된 알림을 직접 재시도시키는 흐름.
 *
 * 권한:
 *  - 본인 여부와 무관하게 OPERATOR 만 가능. 일반 USER 는 본인 알림이라도 직접 재시도 불가.
 *  - 권한 검사는 use case 가 한다 (다른 진입점이 생겨도 같은 검사가 적용되도록).
 *
 * 트랜잭션:
 *  - notification 갱신 + outbox 갱신 + history 적재 — 단일 트랜잭션 (all-or-nothing).
 *  - 트랜잭션 안에서 외부 호출 없음.
 */
@Service
class RetryNotificationService(
    private val notificationRepository: NotificationRepository,
    private val outboxPublisher: OutboxPublisher,
    private val historyRepository: NotificationHistoryRepository,
    private val clock: Clock,
) : RetryNotificationUseCase {

    @Transactional
    override fun execute(command: RetryNotificationCommand): Notification {
        // 1. 권한 검사 — OPERATOR 만 가능
        if (command.requesterRole != RequesterRole.OPERATOR) {
            throw OperatorOnlyException(operationName = "수동 재시도", requesterId = command.requesterId)
        }

        // 2. 알림 존재 확인
        val notification = notificationRepository.findById(command.notificationId)
            ?: throw NotificationNotFoundException(command.notificationId)

        // 3. outbox row 매핑 검증
        val outbox = outboxPublisher.findByNotificationId(notification.requireId())
            ?: throw NotificationDataInconsistencyException(
                "notification(id=${notification.requireId()}) 에 매핑되는 outbox row 가 없음",
            )

        val now = Instant.now(clock)

        // 4. 상태 전제는 도메인 객체에서 검증 — DEAD_LETTER 가 아니면 IllegalStateException
        val resetNotification = notification.resetForManualRetry()
        val resetOutbox = outbox.manualRetry(now)

        notificationRepository.update(resetNotification)
        outboxPublisher.update(resetOutbox)
        historyRepository.append(
            NotificationHistory.of(
                notificationId = notification.requireId(),
                from = NotificationStatus.DEAD_LETTER,
                to = NotificationStatus.PENDING,
                reason = HistoryReason.MANUAL_RETRY,
                now = now,
                detail = "operator(${command.requesterId}) 의 수동 재시도",
            ),
        )
        return resetNotification
    }
}
