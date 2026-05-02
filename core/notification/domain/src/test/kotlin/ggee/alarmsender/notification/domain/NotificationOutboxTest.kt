package ggee.alarmsender.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class NotificationOutboxTest {

    private val now = Instant.parse("2026-05-02T10:00:00Z")
    private val workerId = "worker-1"
    private val leaseDuration = Duration.ofSeconds(30)
    private val retryPolicy = ExponentialBackoffRetryPolicy(maxAttempts = 5, baseDelay = Duration.ofMinutes(1))

    private fun outbox() = NotificationOutbox.create(notificationId = 100L, now = now)

    @Test
    fun `생성 직후 PENDING 상태`() {
        val o = outbox()
        assertEquals(OutboxStatus.PENDING, o.status)
        assertEquals(0, o.attemptCount)
        assertEquals(now, o.availableAt)
    }

    @Test
    fun `claim 은 PENDING 만 가능하며 IN_PROGRESS 와 lease 를 set 한다`() {
        val claimed = outbox().claim(workerId, now, leaseDuration)
        assertEquals(OutboxStatus.IN_PROGRESS, claimed.status)
        assertEquals(workerId, claimed.leaseOwner)
        assertEquals(now.plus(leaseDuration), claimed.leaseExpiresAt)

        assertThrows<IllegalArgumentException> { claimed.claim(workerId, now, leaseDuration) }
    }

    @Test
    fun `availableAt 미도래 row 는 claim 불가`() {
        val future = outbox().copy(availableAt = now.plusSeconds(10))
        assertThrows<IllegalArgumentException> { future.claim(workerId, now, leaseDuration) }
    }

    @Test
    fun `markSucceeded 는 IN_PROGRESS 만 가능하며 lease 를 비운다`() {
        val done = outbox().claim(workerId, now, leaseDuration).markSucceeded(now.plusSeconds(1))
        assertEquals(OutboxStatus.DONE, done.status)
        assertEquals(null, done.leaseOwner)
        assertEquals(null, done.leaseExpiresAt)
    }

    @Test
    fun `markFailedTransient 는 한도 미만이면 PENDING 으로 백오프 후 복귀한다`() {
        var o = outbox().claim(workerId, now, leaseDuration)
        o = o.markFailedTransient("connection refused", now.plusSeconds(1), retryPolicy)
        assertEquals(OutboxStatus.PENDING, o.status)
        assertEquals(1, o.attemptCount)
        // 1번째 실패 후 다음 시도는 1분 뒤
        assertEquals(now.plusSeconds(1).plus(Duration.ofMinutes(1)), o.availableAt)
        assertEquals("connection refused", o.lastError)
        assertEquals(null, o.leaseOwner)
    }

    @Test
    fun `markFailedTransient 는 한도 초과 시 DEAD 로 격리한다`() {
        var o = outbox().copy(attemptCount = 4).claim(workerId, now, leaseDuration)
        o = o.markFailedTransient("smtp 5xx", now.plusSeconds(1), retryPolicy)
        assertEquals(OutboxStatus.DEAD, o.status)
        assertEquals(5, o.attemptCount)
        assertEquals("smtp 5xx", o.lastError)
    }

    @Test
    fun `lease 만료 시 reclaim 은 PENDING 으로 복귀시킨다`() {
        val claimed = outbox().claim(workerId, now, leaseDuration)
        val later = now.plus(leaseDuration).plusSeconds(1)
        val reclaimed = claimed.reclaim(later)
        assertEquals(OutboxStatus.PENDING, reclaimed.status)
        assertEquals(null, reclaimed.leaseOwner)
        assertEquals(null, reclaimed.leaseExpiresAt)
    }

    @Test
    fun `lease 만료 전 reclaim 은 거부된다`() {
        val claimed = outbox().claim(workerId, now, leaseDuration)
        assertThrows<IllegalArgumentException> { claimed.reclaim(now.plusSeconds(1)) }
    }

    @Test
    fun `manualRetry 는 DEAD 만 가능하며 attemptCount 를 0 으로 리셋한다`() {
        val dead = outbox().copy(status = OutboxStatus.DEAD, attemptCount = 5, lastError = "x")
        val retried = dead.manualRetry(now.plusSeconds(100))
        assertEquals(OutboxStatus.PENDING, retried.status)
        assertEquals(0, retried.attemptCount)
        assertEquals(null, retried.lastError)

        assertThrows<IllegalArgumentException> {
            outbox().manualRetry(now)
        }
    }

    @Test
    fun `지수 백오프 시간 계산 검증`() {
        val p = ExponentialBackoffRetryPolicy(maxAttempts = 5, baseDelay = Duration.ofMinutes(1))
        assertEquals(now.plus(Duration.ofMinutes(1)), p.nextAvailableAt(1, now))
        assertEquals(now.plus(Duration.ofMinutes(2)), p.nextAvailableAt(2, now))
        assertEquals(now.plus(Duration.ofMinutes(4)), p.nextAvailableAt(3, now))
        assertEquals(now.plus(Duration.ofMinutes(8)), p.nextAvailableAt(4, now))
        assertEquals(now.plus(Duration.ofMinutes(16)), p.nextAvailableAt(5, now))
    }
}
