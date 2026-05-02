package ggee.alarmsender.notification.usecase.dispatchnotification.port

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
)
