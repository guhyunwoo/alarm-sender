package ggee.alarmsender.notification.usecase.dispatchnotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.NotificationTemplateRepository
import ggee.alarmsender.notification.domain.OutboxStatus
import ggee.alarmsender.notification.domain.RenderedNotification
import ggee.alarmsender.notification.domain.RetryPolicy
import ggee.alarmsender.notification.domain.exception.NotificationDataInconsistencyException
import ggee.alarmsender.notification.platform.email.EmailRequest
import ggee.alarmsender.notification.platform.email.EmailSendResult
import ggee.alarmsender.notification.platform.email.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

/**
 * 워커가 호출하는 발송 처리 유즈케이스.
 *
 * 트랜잭션 경계 (AGENTS.md 트랜잭션 규칙):
 *  1. claim — 단일 SQL (UPDATE … FOR UPDATE SKIP LOCKED RETURNING) 자체가 원자.
 *  2. 외부 발송 호출 — 트랜잭션 밖. 외부 호출이 트랜잭션을 잡고 있으면 안 된다.
 *  3. 결과 반영(outbox + notification + history) — row 단위 새 트랜잭션 (REQUIRES_NEW).
 *     부분 실패는 row 단위로만 롤백돼서 같은 batch 의 다른 row 처리는 계속 진행된다.
 *
 * 사용자 가시 상태와 워커 처리 상태 분리:
 *  - notification.status (PENDING/SENT/DEAD_LETTER) — 사용자가 보는 상태
 *  - outbox.status       (PENDING/IN_PROGRESS/DONE/DEAD) — 워커 내부 상태
 *  발송 중 IN_PROGRESS 는 outbox 에만 있고 사용자에게 노출되지 않는다.
 *
 * IN_APP 채널: 외부 호출 없이 바로 SENT 처리. 클라이언트는 GET /notifications 로 가져간다.
 */
@Service
class DispatchNotificationService(
    private val outboxPublisher: OutboxPublisher,
    private val notificationRepository: NotificationRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val templateRepository: NotificationTemplateRepository,
    private val emailSender: EmailSender,
    private val retryPolicy: RetryPolicy,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) : DispatchNotificationUseCase {

    private val log = LoggerFactory.getLogger(DispatchNotificationService::class.java)

    override fun execute(command: DispatchNotificationCommand): DispatchNotificationResult {
        val now = Instant.now(clock)
        val claimed = outboxPublisher.claim(command.workerId, now, command.leaseDuration, command.batchSize)
        if (claimed.isEmpty()) {
            return DispatchNotificationResult(claimed = 0, succeeded = 0, failed = 0, deadLettered = 0)
        }

        var succeeded = 0
        var failed = 0
        var deadLettered = 0
        var errored = 0

        claimed.forEach { outbox ->
            // row 단위 격리: 한 row 처리 실패가 같은 batch 의 다른 row 까지 막지 않도록.
            // 예외 던진 row 는 IN_PROGRESS 로 남고, lease 만료 뒤 batch reclaim 이 PENDING 으로 되돌린다.
            // 외부 발송이 성공한 뒤 영속화가 실패하면 reclaim 후 재시도되어 중복 발송될 수 있다 — at-least-once 의 본질.
            try {
                val notification = notificationRepository.findById(outbox.notificationId)
                    ?: throw NotificationDataInconsistencyException(
                        "outbox(id=${outbox.id}) 가 가리키는 notification(id=${outbox.notificationId}) 가 없음",
                    )

                val sendResult = trySend(notification)

                when (applyResult(outbox, notification, sendResult)) {
                    Outcome.SUCCEEDED -> succeeded++
                    Outcome.FAILED_TRANSIENT -> failed++
                    Outcome.FAILED_DEAD -> {
                        failed++
                        deadLettered++
                    }
                }
            } catch (ex: Exception) {
                // 분류:
                //  - OptimisticLockingFailureException: lease 만료 후 부활한 stale write — 정상 격리, 중복 발송 가능성
                //  - NotificationDataInconsistencyException: outbox-notification 매핑 깨짐 — 운영 alert 대상
                //  - 그 외 DB/IO 예외: lease 만료 후 reclaim 으로 자동 복구
                errored++
                log.error(
                    "dispatch errored: outboxId={}, notificationId={}, attempt={}. lease 만료되면 reclaim 으로 복구된다.",
                    outbox.id,
                    outbox.notificationId,
                    outbox.attemptCount,
                    ex,
                )
            }
        }

        // claimed = succeeded + failed + errored 는 항상 성립해야 하는 불변식.
        check(claimed.size == succeeded + failed + errored) {
            "dispatch counter mismatch: claimed=${claimed.size}, succeeded=$succeeded, failed=$failed, errored=$errored"
        }
        return DispatchNotificationResult(
            claimed = claimed.size,
            succeeded = succeeded,
            failed = failed,
            deadLettered = deadLettered,
            errored = errored,
        )
    }

    private fun trySend(notification: Notification): EmailSendResult = when (notification.channel) {
        NotificationChannel.EMAIL -> runCatching {
            val rendered = render(notification)
            emailSender.send(
                EmailRequest(
                    to = notification.recipientId,
                    subject = rendered.subject,
                    body = rendered.body,
                ),
            )
        }.getOrElse { ex -> EmailSendResult.TransientFailure(ex.message ?: ex.javaClass.simpleName) }

        // IN_APP: 외부 호출 없이 통일된 워커 흐름으로 SENT 처리.
        // 클라이언트는 GET /api/v1/notifications 로 polling 하여 수신.
        NotificationChannel.IN_APP -> EmailSendResult.Success
    }

    private fun render(notification: Notification) =
        templateRepository.findByTypeAndChannel(notification.type, notification.channel)
            ?.render(notification.payload)
            ?: RenderedNotification(
                subject = notification.payload["title"]?.toString() ?: notification.type.name,
                body = notification.payload["body"]?.toString() ?: notification.payload.toString(),
            )

    private fun applyResult(outbox: NotificationOutbox, notification: Notification, result: EmailSendResult): Outcome =
        // 콜백이 항상 non-null Outcome 을 반환하므로 `!!` 가 안전. 실패는 콜백 안에서 예외로 던져진다 (바깥 try/catch 처리)
        transactionTemplate.execute { _ ->
            val now = Instant.now(clock)
            when (result) {
                is EmailSendResult.Success -> applySuccess(outbox, notification, now)
                is EmailSendResult.TransientFailure -> applyTransientFailure(outbox, notification, result.reason, now)
                is EmailSendResult.PermanentFailure -> applyPermanentFailure(outbox, notification, result.reason, now)
            }
        }!!

    private fun applySuccess(outbox: NotificationOutbox, notification: Notification, now: Instant): Outcome {
        outboxPublisher.update(outbox.markSucceeded(now))
        notificationRepository.update(notification.markSent(now))
        historyRepository.append(
            NotificationHistory.of(
                notificationId = notification.requireId(),
                from = notification.status,
                to = NotificationStatus.SENT,
                reason = HistoryReason.SENT,
                now = now,
            ),
        )
        return Outcome.SUCCEEDED
    }

    private fun applyTransientFailure(outbox: NotificationOutbox, notification: Notification, reason: String, now: Instant): Outcome {
        val updatedOutbox = outbox.markFailedTransient(reason, now, retryPolicy)
        outboxPublisher.update(updatedOutbox)
        return if (updatedOutbox.status == OutboxStatus.DEAD) {
            quarantine(notification, HistoryReason.EXHAUSTED, reason, now)
            Outcome.FAILED_DEAD
        } else {
            historyRepository.append(
                NotificationHistory.of(
                    notificationId = notification.requireId(),
                    from = notification.status,
                    to = notification.status,
                    reason = HistoryReason.TRANSIENT_FAILURE,
                    now = now,
                    detail = reason,
                ),
            )
            Outcome.FAILED_TRANSIENT
        }
    }

    private fun applyPermanentFailure(outbox: NotificationOutbox, notification: Notification, reason: String, now: Instant): Outcome {
        // 영구 실패는 재시도하지 않고 즉시 DEAD 격리. attempt_count 는 도메인 메서드가 +1 만 해서
        // 실제 시도 횟수를 그대로 둔다 (한도까지 건너뛰지 않는다). 한도 소진과 영구 실패의 구분은 history.reason 으로.
        outboxPublisher.update(outbox.markFailedPermanent(reason, now))
        quarantine(notification, HistoryReason.PERMANENT_FAILURE, reason, now)
        return Outcome.FAILED_DEAD
    }

    private fun quarantine(notification: Notification, reason: HistoryReason, detail: String, now: Instant) {
        notificationRepository.update(notification.markDeadLetter())
        historyRepository.append(
            NotificationHistory.of(
                notificationId = notification.requireId(),
                from = notification.status,
                to = NotificationStatus.DEAD_LETTER,
                reason = reason,
                now = now,
                detail = detail,
            ),
        )
    }

    private enum class Outcome { SUCCEEDED, FAILED_TRANSIENT, FAILED_DEAD }
}
