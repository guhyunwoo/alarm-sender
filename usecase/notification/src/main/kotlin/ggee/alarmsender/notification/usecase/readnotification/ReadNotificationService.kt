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
 * 도메인 객체의 markAsRead 는 객체 단위 멱등성을 보장하고,
 * 실제 동시성은 저장소의 조건부 UPDATE(read_at IS NULL) 로 한 번만 성공하게 한다.
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
        if (!repository.markAsReadIfUnread(notification.requireId(), now)) {
            val alreadyRead = repository.findById(command.notificationId)
                ?: throw NotificationNotFoundException(command.notificationId)
            return ReadNotificationResult(alreadyRead, newlyRead = false)
        }

        val updated = notification.markAsRead(now)
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
