package ggee.alarmsender.notification.usecase.reclaimnotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.usecase.reclaimnotification.ReclaimCommand
import ggee.alarmsender.notification.usecase.reclaimnotification.ReclaimNotificationUseCase
import ggee.alarmsender.notification.usecase.reclaimnotification.ReclaimResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * lease 만료된 IN_PROGRESS outbox row 를 PENDING 으로 복귀시킨다.
 *
 * 워커 죽음 / GC pause / 네트워크 단절 등 정상 종료 신호가 없는 모든 상황에서
 * lease_expires_at 만 보고 reclaim 한다.
 */
@Service
class ReclaimNotificationService(
    private val outboxRepository: NotificationOutboxRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val clock: Clock,
) : ReclaimNotificationUseCase {

    @Transactional
    override fun execute(command: ReclaimCommand): ReclaimResult {
        val now = Instant.now(clock)
        val expired = outboxRepository.findExpired(now, command.limit)
        expired.forEach { outbox ->
            val reclaimed = outbox.reclaim(now)
            outboxRepository.update(reclaimed)
            historyRepository.append(
                NotificationHistory.of(
                    notificationId = outbox.notificationId,
                    from = null,
                    to = ggee.alarmsender.notification.domain.NotificationStatus.PENDING,
                    reason = HistoryReason.RECLAIMED,
                    now = now,
                    detail = "lease=${outbox.leaseExpiresAt} owner=${outbox.leaseOwner}",
                ),
            )
        }
        return ReclaimResult(reclaimed = expired.size)
    }
}
