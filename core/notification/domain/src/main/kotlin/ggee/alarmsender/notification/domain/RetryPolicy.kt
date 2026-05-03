package ggee.alarmsender.notification.domain

import java.time.Duration
import java.time.Instant

/**
 * 재시도 백오프 정책. 도메인의 외부 협력자가 아닌 정책 그 자체이므로 도메인에서 인터페이스로 정의한다.
 */
interface RetryPolicy {
    val maxAttempts: Int

    /**
     * `attemptCount` 가 한도를 초과했는가.
     * 예: maxAttempts=5 이면 5번째 시도까지 허용, 6번째부터 isExhausted=true
     */
    fun isExhausted(attemptCount: Int): Boolean

    /**
     * 다음 시도 가능 시각. 지수 백오프 등.
     */
    fun nextAvailableAt(attemptCount: Int, now: Instant): Instant
}

/**
 * 1m → 2m → 4m → 8m → 16m, 최대 5회.
 */
class ExponentialBackoffRetryPolicy(
    override val maxAttempts: Int = 5,
    private val baseDelay: Duration = Duration.ofMinutes(1),
) : RetryPolicy {
    override fun isExhausted(attemptCount: Int): Boolean = attemptCount >= maxAttempts

    override fun nextAvailableAt(attemptCount: Int, now: Instant): Instant {
        require(attemptCount >= 1) { "attemptCount 는 1 이상이어야 한다" }
        val multiplier = 1L shl (attemptCount - 1)
        return now.plus(baseDelay.multipliedBy(multiplier))
    }
}
