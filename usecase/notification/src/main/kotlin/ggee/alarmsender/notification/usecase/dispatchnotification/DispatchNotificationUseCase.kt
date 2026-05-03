package ggee.alarmsender.notification.usecase.dispatchnotification

import java.time.Duration

interface DispatchNotificationUseCase {
    fun execute(command: DispatchNotificationCommand): DispatchNotificationResult
}

data class DispatchNotificationCommand(
    val workerId: String,
    val batchSize: Int,
    val leaseDuration: Duration,
)

data class DispatchNotificationResult(
    val claimed: Int,
    val succeeded: Int,
    val failed: Int,
    val deadLettered: Int,
    /**
     * 처리 도중 예외가 발생해 outcome 분기에 도달하지 못한 row 수.
     * (DataInconsistency / OptimisticLock / DB 오류 등 — lease 만료 후 reclaim 대상)
     */
    val errored: Int = 0,
) {
    init {
        require(claimed == succeeded + failed + errored) {
            "counter mismatch: claimed=$claimed, succeeded=$succeeded, failed=$failed, errored=$errored"
        }
    }
}
