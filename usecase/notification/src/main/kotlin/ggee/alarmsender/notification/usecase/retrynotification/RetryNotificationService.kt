package ggee.alarmsender.notification.usecase.retrynotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationDataInconsistencyException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.usecase.dispatchnotification.OutboxPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 운영자(또는 알림 수신자) 가 DEAD_LETTER 격리된 알림을 명시적으로 재시도하는 흐름.
 *
 * 트랜잭션:
 *  - notification 갱신 + outbox 갱신 + history 적재가 단일 트랜잭션 (all-or-nothing).
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
        val notification = notificationRepository.findById(command.notificationId)
            ?: throw NotificationNotFoundException(command.notificationId)
        if (notification.recipientId != command.requesterId) {
            throw NotificationAccessDeniedException(command.notificationId, command.requesterId)
        }

        val outbox = outboxPublisher.findByNotificationId(notification.requireId())
            ?: throw NotificationDataInconsistencyException(
                "notification(id=${notification.requireId()}) 의 outbox row 가 존재하지 않음",
            )

        val now = Instant.now(clock)

        // 도메인 객체가 상태 검증 + 전이 책임 — DEAD_LETTER 가 아니면 IllegalStateException 발생
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
                detail = "수동 재시도 by ${command.requesterId}",
            ),
        )
        return resetNotification
    }
}
