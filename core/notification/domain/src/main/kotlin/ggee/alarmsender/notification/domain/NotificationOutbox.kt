package ggee.alarmsender.notification.domain

import java.time.Duration
import java.time.Instant

/**
 * 비동기 발송 처리 큐 row.
 *
 * 워커가 `claim` → 발송 → `markSucceeded` / `markFailedTransient` 흐름으로 진행한다.
 * 모든 상태 전이 규칙은 이 객체가 책임진다.
 */
data class NotificationOutbox(
    val id: Long? = null,
    val notificationId: Long,
    val status: OutboxStatus,
    val availableAt: Instant,
    val attemptCount: Int = 0,
    val leaseOwner: String? = null,
    val leaseExpiresAt: Instant? = null,
    val lastError: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * 낙관적 락 버전. 영속 계층이 관리하며 도메인 메서드는 그대로 전파한다.
     * stale write (lease 만료 후 죽은 워커 부활) 차단 토큰.
     */
    val version: Long = 0L,
) {
    fun claim(workerId: String, now: Instant, leaseDuration: Duration): NotificationOutbox {
        require(status == OutboxStatus.PENDING) { "PENDING 이 아닌 row 를 claim 할 수 없다 (현재: ${'$'}status)" }
        require(!availableAt.isAfter(now)) { "availableAt(${'$'}availableAt) 이 아직 도래하지 않았다" }
        return copy(
            status = OutboxStatus.IN_PROGRESS,
            leaseOwner = workerId,
            leaseExpiresAt = now.plus(leaseDuration),
            updatedAt = now,
        )
    }

    fun markSucceeded(now: Instant): NotificationOutbox {
        require(status == OutboxStatus.IN_PROGRESS) { "IN_PROGRESS 가 아닌 row 를 success 처리 불가 (현재: ${'$'}status)" }
        return copy(
            status = OutboxStatus.DONE,
            leaseOwner = null,
            leaseExpiresAt = null,
            lastError = null,
            updatedAt = now,
        )
    }

    /**
     * 일시 실패. 정책에 따라 재시도 일정으로 복귀하거나 한도 초과 시 DEAD 격리.
     */
    fun markFailedTransient(error: String, now: Instant, retryPolicy: RetryPolicy): NotificationOutbox {
        require(status == OutboxStatus.IN_PROGRESS) { "IN_PROGRESS 가 아닌 row 를 failure 처리 불가 (현재: ${'$'}status)" }
        val nextAttempt = attemptCount + 1
        return if (retryPolicy.isExhausted(nextAttempt)) {
            copy(
                status = OutboxStatus.DEAD,
                attemptCount = nextAttempt,
                leaseOwner = null,
                leaseExpiresAt = null,
                lastError = error,
                updatedAt = now,
            )
        } else {
            copy(
                status = OutboxStatus.PENDING,
                attemptCount = nextAttempt,
                availableAt = retryPolicy.nextAvailableAt(nextAttempt, now),
                leaseOwner = null,
                leaseExpiresAt = null,
                lastError = error,
                updatedAt = now,
            )
        }
    }

    /**
     * lease 만료 시 다른 워커 또는 배치가 PENDING 으로 복귀시킨다.
     * 정상 종료가 아닌 워커 죽음·GC pause 등을 신호 없이 처리하는 경로.
     */
    fun reclaim(now: Instant): NotificationOutbox {
        require(status == OutboxStatus.IN_PROGRESS) { "IN_PROGRESS 가 아닌 row 는 reclaim 불가 (현재: ${'$'}status)" }
        require(leaseExpiresAt != null && leaseExpiresAt.isBefore(now)) {
            "lease 가 아직 만료되지 않았다 (lease=${'$'}leaseExpiresAt, now=${'$'}now)"
        }
        return copy(
            status = OutboxStatus.PENDING,
            leaseOwner = null,
            leaseExpiresAt = null,
            updatedAt = now,
        )
    }

    /**
     * 운영자 명시적 의지의 수동 재시도. attemptCount 를 0 으로 리셋.
     */
    fun manualRetry(now: Instant): NotificationOutbox {
        require(status == OutboxStatus.DEAD) { "DEAD 가 아닌 row 는 수동 재시도 불가 (현재: ${'$'}status)" }
        return copy(
            status = OutboxStatus.PENDING,
            attemptCount = 0,
            availableAt = now,
            lastError = null,
            updatedAt = now,
        )
    }

    fun requireId(): Long = id ?: error("영속화되지 않은 NotificationOutbox")

    companion object {
        fun create(notificationId: Long, now: Instant): NotificationOutbox =
            NotificationOutbox(
                notificationId = notificationId,
                status = OutboxStatus.PENDING,
                availableAt = now,
                createdAt = now,
                updatedAt = now,
            )
    }
}
