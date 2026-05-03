package ggee.alarmsender.notification.usecase.reclaimnotification

interface ReclaimNotificationUseCase {
    fun execute(command: ReclaimCommand): ReclaimResult
}

data class ReclaimCommand(
    val limit: Int,
)

data class ReclaimResult(
    val reclaimed: Int,
)
