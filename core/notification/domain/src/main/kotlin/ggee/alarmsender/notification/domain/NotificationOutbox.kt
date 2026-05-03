package ggee.alarmsender.notification.domain

import java.time.Duration
import java.time.Instant

/**
 * 비동기 발송 처리 큐 row.
 * 워커가 claim → 발송 → markSucceeded / markFailedTransient 순서로 흘려보낸다.
 * 상태 전이 규칙은 이 객체가 직접 가진다.
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
     * 낙관적 락 버전. 영속 계층이 관리하고 도메인 메서드는 그대로 들고 다닌다.
     * lease 만료 후 죽은 줄 알았던 워커가 부활해도 stale write 를 막는 토큰.
     */
    val version: Long = 0L,
) {
    fun claim(workerId: String, now: Instant, leaseDuration: Duration): NotificationOutbox {
        require(status == OutboxStatus.PENDING) { "PENDING 만 claim 가능 (현재: $status)" }
        require(!availableAt.isAfter(now)) { "availableAt($availableAt) 이 아직 안 됐다 (now=$now)" }
        return copy(
            status = OutboxStatus.IN_PROGRESS,
            leaseOwner = workerId,
            leaseExpiresAt = now.plus(leaseDuration),
            updatedAt = now,
        )
    }

    fun markSucceeded(now: Instant): NotificationOutbox {
        require(status == OutboxStatus.IN_PROGRESS) { "IN_PROGRESS 만 success 처리 가능 (현재: $status)" }
        return copy(
            status = OutboxStatus.DONE,
            leaseOwner = null,
            leaseExpiresAt = null,
            lastError = null,
            updatedAt = now,
        )
    }

    /**
     * 영구 실패. 첫 시도라도 즉시 DEAD 격리. attemptCount 는 실제 시도 횟수 그대로 둬서
     * 운영 추적용으로 남긴다 (한도 소진과 영구 실패의 구분은 history.reason 으로).
     */
    fun markFailedPermanent(error: String, now: Instant): NotificationOutbox {
        require(status == OutboxStatus.IN_PROGRESS) {
            "IN_PROGRESS 만 permanent failure 처리 가능 (현재: $status)"
        }
        return copy(
            status = OutboxStatus.DEAD,
            attemptCount = attemptCount + 1,
            leaseOwner = null,
            leaseExpiresAt = null,
            lastError = error,
            updatedAt = now,
        )
    }

    /**
     * 일시 실패. 정책에 따라 재시도 일정으로 되돌아가거나 한도 초과 시 DEAD 격리.
     */
    fun markFailedTransient(error: String, now: Instant, retryPolicy: RetryPolicy): NotificationOutbox {
        require(status == OutboxStatus.IN_PROGRESS) { "IN_PROGRESS 만 failure 처리 가능 (현재: $status)" }
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
     * lease 만료 시 다른 워커나 배치가 PENDING 으로 되돌린다.
     * 워커가 정상 종료 신호 없이 죽거나 GC pause 에 빠진 경우의 복구 경로.
     */
    fun reclaim(now: Instant): NotificationOutbox {
        require(status == OutboxStatus.IN_PROGRESS) { "IN_PROGRESS 만 reclaim 가능 (현재: $status)" }
        require(leaseExpiresAt != null && leaseExpiresAt.isBefore(now)) {
            "lease 가 아직 살아있다 (lease=$leaseExpiresAt, now=$now)"
        }
        return copy(
            status = OutboxStatus.PENDING,
            leaseOwner = null,
            leaseExpiresAt = null,
            updatedAt = now,
        )
    }

    /**
     * 운영자 명시 재시도. attemptCount 를 0 으로 리셋한다.
     */
    fun manualRetry(now: Instant): NotificationOutbox {
        require(status == OutboxStatus.DEAD) { "DEAD 만 수동 재시도 가능 (현재: $status)" }
        return copy(
            status = OutboxStatus.PENDING,
            attemptCount = 0,
            availableAt = now,
            lastError = null,
            updatedAt = now,
        )
    }

    fun requireId(): Long = id ?: error("아직 영속화되지 않은 NotificationOutbox")

    companion object {
        fun create(notificationId: Long, now: Instant, availableAt: Instant = now): NotificationOutbox =
            NotificationOutbox(
                notificationId = notificationId,
                status = OutboxStatus.PENDING,
                availableAt = availableAt,
                createdAt = now,
                updatedAt = now,
            )
    }
}
