package ggee.alarmsender.notification.usecase.sendnotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.usecase.sendnotification.SendNotificationCommand
import ggee.alarmsender.notification.usecase.sendnotification.SendNotificationResult
import ggee.alarmsender.notification.usecase.sendnotification.SendNotificationUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 알림 적재 + Outbox 적재를 단일 트랜잭션으로 묶는다. (AGENTS.md 트랜잭션 규칙: 두 write 의 all-or-nothing 필요)
 *
 * 멱등성 검사 순서:
 *  1. idempotency_key (있다면) 로 기존 알림 조회 → 있으면 그대로 반환 (deduplicated=true)
 *  2. (recipient, type, ref) 자연 키로 기존 알림 조회 → 있으면 반환
 *  3. 없으면 신규 적재
 *
 * 동시성: 두 클라이언트가 동시에 같은 키로 요청해 둘 다 1단계를 미스해 신규 INSERT 를 시도해도,
 * DB UNIQUE 제약이 마지막 보호선으로 동작한다 (DataIntegrityViolationException). 호출자(컨트롤러)는
 * 충돌 발생 시 한 번 더 조회해 기존 row 를 돌려주는 정책을 취한다.
 */
@Service
class SendNotificationService(
    private val notificationRepository: NotificationRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val clock: Clock,
) : SendNotificationUseCase {

    @Transactional
    override fun execute(command: SendNotificationCommand): SendNotificationResult {
        val now = Instant.now(clock)

        command.idempotencyKey?.let { key ->
            notificationRepository.findByIdempotencyKey(key)?.let {
                return SendNotificationResult(it, deduplicated = true)
            }
        }

        if (command.refType != null && command.refId != null) {
            notificationRepository.findByNaturalKey(command.recipientId, command.type, command.refType, command.refId)?.let {
                return SendNotificationResult(it, deduplicated = true)
            }
        }

        val saved = notificationRepository.save(
            Notification.create(
                recipientId = command.recipientId,
                type = command.type,
                channel = command.channel,
                payload = command.payload,
                idempotencyKey = command.idempotencyKey,
                refType = command.refType,
                refId = command.refId,
                now = now,
            ),
        )
        outboxRepository.save(NotificationOutbox.create(notificationId = saved.requireId(), now = now))
        historyRepository.append(
            NotificationHistory.of(
                notificationId = saved.requireId(),
                from = null,
                to = NotificationStatus.PENDING,
                reason = HistoryReason.CREATED,
                now = now,
            ),
        )
        return SendNotificationResult(saved, deduplicated = false)
    }
}
