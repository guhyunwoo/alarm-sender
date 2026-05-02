package ggee.alarmsender.notification.domain

enum class OutboxStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    DEAD,
}
