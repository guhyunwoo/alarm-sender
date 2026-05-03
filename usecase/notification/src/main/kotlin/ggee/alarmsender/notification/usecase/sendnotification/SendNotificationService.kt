package ggee.alarmsender.notification.usecase.sendnotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationOutboxRepository
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

/**
 * 알림 적재 + Outbox 적재 + history 적재를 단일 트랜잭션으로 묶는다.
 * (AGENTS.md 트랜잭션 규칙: 세 write 의 all-or-nothing 필요)
 *
 * 멱등성 검사 순서:
 *  1. idempotency_key 로 기존 알림 조회 → 있으면 그대로 반환 (deduplicated=true)
 *  2. (recipient, type, ref_type, ref_id) 자연 키로 기존 알림 조회 → 있으면 반환
 *  3. 없으면 신규 적재
 *
 * 동시성 race: 두 클라이언트가 동시에 같은 키로 1단계를 미스하고 둘 다 INSERT 를 시도하면,
 * DB UNIQUE 제약이 마지막 보호선으로 동작해 [DataIntegrityViolationException] 발생.
 * 본 서비스는 이를 use case 내부에서 catch 하여 재조회 후 기존 알림을 반환한다.
 * 컨트롤러는 충돌 처리 책임을 갖지 않는다.
 *
 * **호출 계약 (중요)**: 본 서비스는 **트랜잭션 밖에서 호출되어야** 한다.
 * 이유는 1차/2차 read 가 트랜잭션 밖에서 실행되어야 다른 트랜잭션의 commit 결과를 볼 수 있기 때문.
 * 만약 outer @Transactional 메서드에서 호출하면 read 가 outer snapshot 에 묶여 race-fallback
 * read 가 null 을 받게 된다 (특히 REPEATABLE_READ 격리). 호출 계약 위반은 프로그래머 오류.
 *
 * 트랜잭션 분리: 1차 read 는 트랜잭션 밖 (멱등 hit 이 다수, read-only). 신규 INSERT 만 새 트랜잭션
 * (REQUIRES_NEW). race-fallback read 는 INSERT 트랜잭션이 롤백된 뒤이므로 다시 트랜잭션 밖에서 수행 —
 * 이렇게 해야 다른 커밋된 트랜잭션의 결과를 볼 수 있다.
 */
@Service
class SendNotificationService(
    private val notificationRepository: NotificationRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) : SendNotificationUseCase {

    override fun execute(command: SendNotificationCommand): SendNotificationResult {
        // 1차 멱등 검사 — 트랜잭션 밖 read
        findExisting(command)?.let { return SendNotificationResult(it, deduplicated = true) }

        // 신규 적재 — 단일 트랜잭션 (notification + outbox + history)
        val saved = try {
            transactionTemplate.execute { _ -> persistNew(command) }!!
        } catch (ex: DataIntegrityViolationException) {
            // race: 다른 트랜잭션이 같은 키로 먼저 commit. 트랜잭션 밖에서 다시 조회.
            return findExisting(command)
                ?.let { SendNotificationResult(it, deduplicated = true) }
                ?: throw ex
        }
        return SendNotificationResult(saved, deduplicated = false)
    }

    private fun findExisting(command: SendNotificationCommand): Notification? {
        command.idempotencyKey?.let { key ->
            notificationRepository.findByIdempotencyKey(key)?.let { return it }
        }
        if (command.refType != null && command.refId != null) {
            notificationRepository.findByNaturalKey(command.recipientId, command.type, command.refType, command.refId)
                ?.let { return it }
        }
        return null
    }

    private fun persistNew(command: SendNotificationCommand): Notification {
        val now = Instant.now(clock)
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
        return saved
    }
}
