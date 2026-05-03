package ggee.alarmsender.notification.usecase.readnotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 멀티 디바이스 동시 read 시에도 readAt 은 한 번만 set, 이후 호출은 no-op.
 *
 * 도메인 객체의 markAsRead 가 멱등성을 보장하므로 이 서비스는
 * 권한 검사(본인 알림인가)와 영속화/이력 적재 정도만 책임진다.
 */
@Service
class ReadNotificationService(
    private val repository: NotificationRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val clock: Clock,
) : ReadNotificationUseCase {

    @Transactional
    override fun execute(command: ReadNotificationCommand): ReadNotificationResult {
        val notification = repository.findById(command.notificationId)
            ?: throw NotificationNotFoundException(command.notificationId)
        if (notification.recipientId != command.requesterId) {
            throw NotificationAccessDeniedException(command.notificationId, command.requesterId)
        }

        if (notification.readAt != null) {
            return ReadNotificationResult(notification, newlyRead = false)
        }

        val now = Instant.now(clock)
        val updated = repository.update(notification.markAsRead(now))
        historyRepository.append(
            NotificationHistory.of(
                notificationId = updated.requireId(),
                from = updated.status,
                to = updated.status,
                reason = HistoryReason.READ,
                now = now,
            ),
        )
        return ReadNotificationResult(updated, newlyRead = true)
    }
}
