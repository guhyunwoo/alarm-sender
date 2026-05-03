package ggee.alarmsender.notification.usecase.dispatchnotification

import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationHistory
import ggee.alarmsender.notification.domain.NotificationHistoryRepository
import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.OutboxStatus
import ggee.alarmsender.notification.domain.RetryPolicy
import ggee.alarmsender.notification.platform.email.EmailRequest
import ggee.alarmsender.notification.platform.email.EmailSendResult
import ggee.alarmsender.notification.platform.email.EmailSender
import ggee.alarmsender.notification.usecase.dispatchnotification.DispatchNotificationCommand
import ggee.alarmsender.notification.usecase.dispatchnotification.DispatchNotificationResult
import ggee.alarmsender.notification.usecase.dispatchnotification.DispatchNotificationUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

/**
 * 워커가 호출하는 발송 처리 유즈케이스.
 *
 * 트랜잭션 경계 (AGENTS.md 트랜잭션 규칙):
 *  1. claimBatch — 단일 SQL (UPDATE … FOR UPDATE SKIP LOCKED RETURNING) 자체가 원자.
 *  2. 외부 발송 호출 — 트랜잭션 밖. 외부 호출이 트랜잭션을 점유하면 안 된다.
 *  3. 결과 반영(outbox 갱신 + history 적재) — 한 row 단위로 새 트랜잭션. 부분 실패 시 row 단위로만 롤백.
 */
@Service
class DispatchNotificationService(
    private val outboxPublisher: OutboxPublisher,
    private val notificationRepository: NotificationRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val emailSender: EmailSender,
    private val retryPolicy: RetryPolicy,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) : DispatchNotificationUseCase {

    override fun execute(command: DispatchNotificationCommand): DispatchNotificationResult {
        val now = Instant.now(clock)
        val claimed = outboxPublisher.claim(command.workerId, now, command.leaseDuration, command.batchSize)
        if (claimed.isEmpty()) {
            return DispatchNotificationResult(claimed = 0, succeeded = 0, failed = 0, deadLettered = 0)
        }

        var succeeded = 0
        var failed = 0
        var deadLettered = 0

        claimed.forEach { outbox ->
            val notification = notificationRepository.findById(outbox.notificationId)
                ?: error("outbox row 의 notification(${outbox.notificationId}) 가 존재하지 않는다")

            val sendResult = trySend(notification)

            when (applyResult(outbox, notification, sendResult)) {
                Outcome.SUCCEEDED -> succeeded++
                Outcome.FAILED_TRANSIENT -> failed++
                Outcome.FAILED_DEAD -> {
                    failed++
                    deadLettered++
                }
            }
        }
        return DispatchNotificationResult(claimed = claimed.size, succeeded = succeeded, failed = failed, deadLettered = deadLettered)
    }

    private fun trySend(notification: Notification): EmailSendResult = when (notification.channel) {
        NotificationChannel.EMAIL -> runCatching {
            emailSender.send(
                EmailRequest(
                    to = notification.recipientId,
                    subject = notification.payload["title"]?.toString() ?: notification.type.name,
                    body = notification.payload["body"]?.toString() ?: notification.payload.toString(),
                ),
            )
        }.getOrElse { ex -> EmailSendResult.TransientFailure(ex.message ?: ex.javaClass.simpleName) }

        NotificationChannel.IN_APP -> EmailSendResult.Success // DB 적재로 발송 완료. 클라이언트가 polling/SSE 로 수신.
    }

    private fun applyResult(outbox: NotificationOutbox, notification: Notification, result: EmailSendResult): Outcome =
        transactionTemplate.execute { _ ->
            val now = Instant.now(clock)
            when (result) {
                is EmailSendResult.Success -> {
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
                    Outcome.SUCCEEDED
                }
                is EmailSendResult.TransientFailure -> {
                    val updatedOutbox = outbox.markFailedTransient(result.reason, now, retryPolicy)
                    outboxPublisher.update(updatedOutbox)
                    if (updatedOutbox.status == OutboxStatus.DEAD) {
                        notificationRepository.update(notification.markDeadLetter())
                        historyRepository.append(
                            NotificationHistory.of(
                                notificationId = notification.requireId(),
                                from = notification.status,
                                to = NotificationStatus.DEAD_LETTER,
                                reason = HistoryReason.EXHAUSTED,
                                now = now,
                                detail = result.reason,
                            ),
                        )
                        Outcome.FAILED_DEAD
                    } else {
                        historyRepository.append(
                            NotificationHistory.of(
                                notificationId = notification.requireId(),
                                from = notification.status,
                                to = notification.status,
                                reason = HistoryReason.TRANSIENT_FAILURE,
                                now = now,
                                detail = result.reason,
                            ),
                        )
                        Outcome.FAILED_TRANSIENT
                    }
                }
            }
        } ?: Outcome.FAILED_TRANSIENT

    private enum class Outcome { SUCCEEDED, FAILED_TRANSIENT, FAILED_DEAD }
}
