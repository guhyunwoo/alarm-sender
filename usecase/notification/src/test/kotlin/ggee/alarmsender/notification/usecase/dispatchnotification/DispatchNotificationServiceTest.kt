package ggee.alarmsender.notification.usecase.dispatchnotification

import ggee.alarmsender.notification.domain.ExponentialBackoffRetryPolicy
import ggee.alarmsender.notification.domain.HistoryReason
import ggee.alarmsender.notification.domain.NotificationOutbox
import ggee.alarmsender.notification.domain.NotificationStatus
import ggee.alarmsender.notification.domain.OutboxStatus
import ggee.alarmsender.notification.platform.email.EmailSendResult
import ggee.alarmsender.notification.platform.email.InMemoryEmailSender
import ggee.alarmsender.notification.testfixture.NotificationFixtures
import ggee.alarmsender.notification.teststub.InMemoryNotificationHistoryRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationOutboxRepository
import ggee.alarmsender.notification.teststub.InMemoryNotificationRepository
import ggee.alarmsender.notification.usecase.dispatchnotification.DispatchNotificationCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DispatchNotificationServiceTest {

    private val now = Instant.parse("2026-05-02T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val notifications = InMemoryNotificationRepository()
    private val outbox = InMemoryNotificationOutboxRepository()
    private val history = InMemoryNotificationHistoryRepository()
    private val emailSender = InMemoryEmailSender()
    private val retryPolicy = ExponentialBackoffRetryPolicy(maxAttempts = 5, baseDelay = Duration.ofMinutes(1))

    /** 단위 테스트에서는 트랜잭션을 흉내 내지 않고 콜백을 그대로 실행. */
    private val transactionTemplate = TransactionTemplate(NoOpTransactionManager())

    private val outboxPublisher: OutboxPublisher = DbPollingOutboxPublisher(outbox)

    private val sut = DispatchNotificationService(
        outboxPublisher = outboxPublisher,
        notificationRepository = notifications,
        historyRepository = history,
        emailSender = emailSender,
        retryPolicy = retryPolicy,
        clock = clock,
        transactionTemplate = transactionTemplate,
    )

    @BeforeEach
    fun setUp() {
        notifications.clear()
        outbox.clear()
        history.clear()
        emailSender.clear()
    }

    private fun seedReadyOutbox(idempotencyKey: String = "k1"): NotificationOutbox {
        val n = notifications.save(NotificationFixtures.notification(idempotencyKey = idempotencyKey, refId = idempotencyKey))
        return outbox.save(NotificationFixtures.outbox(notificationId = n.id!!, availableAt = now))
    }

    private fun command() = DispatchNotificationCommand(workerId = "w-1", batchSize = 10, leaseDuration = Duration.ofSeconds(30))

    @Test
    fun `발송 성공 — outbox DONE, notification SENT, history SENT 적재`() {
        val o = seedReadyOutbox()
        val result = sut.execute(command())

        assertEquals(1, result.claimed)
        assertEquals(1, result.succeeded)
        assertEquals(0, result.failed)

        val outboxAfter = outbox.findById(o.id!!)
        assertEquals(OutboxStatus.DONE, outboxAfter?.status)
        val notificationAfter = notifications.findById(o.notificationId)
        assertEquals(NotificationStatus.SENT, notificationAfter?.status)
        assertNotNull(notificationAfter?.sentAt)
        assertEquals(1, history.all().size)
    }

    @Test
    fun `일시 실패 — outbox PENDING 으로 복귀, attempt 증가, 다음 availableAt 백오프`() {
        val o = seedReadyOutbox("retry")
        emailSender.nextResult = EmailSendResult.TransientFailure("smtp 5xx")

        val result = sut.execute(command())
        assertEquals(1, result.failed)
        assertEquals(0, result.succeeded)

        val outboxAfter = outbox.findById(o.id!!)
        assertEquals(OutboxStatus.PENDING, outboxAfter?.status)
        assertEquals(1, outboxAfter?.attemptCount)
        // 1회 실패 → 1분 뒤로 백오프
        assertEquals(now.plus(Duration.ofMinutes(1)), outboxAfter?.availableAt)
        assertEquals("smtp 5xx", outboxAfter?.lastError)
    }

    @Test
    fun `재시도 한도 초과 — outbox DEAD, notification DEAD_LETTER, history EXHAUSTED`() {
        val n = notifications.save(NotificationFixtures.notification(idempotencyKey = "exhaust"))
        // 4회 실패 상태 (다음 실패 = 5회째이므로 한도 초과)
        outbox.save(NotificationFixtures.outbox(notificationId = n.id!!, availableAt = now, attemptCount = 4))
        emailSender.nextResult = EmailSendResult.TransientFailure("permanent issue")

        val result = sut.execute(command())
        assertEquals(1, result.deadLettered)

        val outboxAfter = outbox.all().single()
        assertEquals(OutboxStatus.DEAD, outboxAfter.status)
        assertEquals(5, outboxAfter.attemptCount)
        val notificationAfter = notifications.findById(n.id!!)
        assertEquals(NotificationStatus.DEAD_LETTER, notificationAfter?.status)
    }

    @Test
    fun `claimable 한 row 가 없으면 0건 반환`() {
        val result = sut.execute(command())
        assertEquals(0, result.claimed)
        assertEquals(0, emailSender.all().size)
    }

    @Test
    fun `영구 실패 — 첫 시도라도 즉시 DEAD 격리, attempt_count=1, history PERMANENT_FAILURE`() {
        val o = seedReadyOutbox("permanent")
        emailSender.nextResult = EmailSendResult.PermanentFailure("invalid email address")

        val result = sut.execute(command())
        assertEquals(1, result.deadLettered, "영구 실패는 즉시 DEAD")
        assertEquals(0, result.succeeded)

        val outboxAfter = outbox.findById(o.id!!)
        assertEquals(OutboxStatus.DEAD, outboxAfter?.status)
        assertEquals(1, outboxAfter?.attemptCount, "영구 실패 시 attempt_count 점프 금지 — 실제 시도 횟수 1 유지")
        assertEquals("invalid email address", outboxAfter?.lastError)

        val notificationAfter = notifications.findById(o.notificationId)
        assertEquals(NotificationStatus.DEAD_LETTER, notificationAfter?.status)

        // history.reason 으로 영구 실패 vs 한도 소진 구분
        val histories = history.findByNotificationId(o.notificationId)
        val deadEntry = histories.last()
        assertEquals(HistoryReason.PERMANENT_FAILURE, deadEntry.reason)
    }

    @Test
    fun `dispatch counter invariant — claimed = succeeded + failed + errored`() {
        // 정상 발송 1건
        seedReadyOutbox("ok-1")
        val result = sut.execute(command())
        assertEquals(result.claimed, result.succeeded + result.failed + result.errored)
    }

    /** TransactionTemplate 가 콜백 호출만 위임하는 fake. 단위 테스트 한정. */
    private class NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: org.springframework.transaction.TransactionDefinition?): TransactionStatus =
            FakeStatus()

        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit

        private class FakeStatus : TransactionStatus {
            override fun hasSavepoint(): Boolean = false
            override fun setRollbackOnly() = Unit
            override fun isRollbackOnly(): Boolean = false
            override fun flush() = Unit
            override fun isCompleted(): Boolean = false
            override fun isNewTransaction(): Boolean = true
            override fun createSavepoint(): Any = Any()
            override fun rollbackToSavepoint(savepoint: Any) = Unit
            override fun releaseSavepoint(savepoint: Any) = Unit
        }
    }
}
