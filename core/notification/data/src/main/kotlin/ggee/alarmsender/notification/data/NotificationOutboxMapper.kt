package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationOutbox

internal fun NotificationOutboxEntity.toDomain(): NotificationOutbox = NotificationOutbox(
    id = id,
    notificationId = notificationId,
    status = status,
    availableAt = availableAt,
    attemptCount = attemptCount,
    leaseOwner = leaseOwner,
    leaseExpiresAt = leaseExpiresAt,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
)

internal fun NotificationOutbox.toEntity(): NotificationOutboxEntity = NotificationOutboxEntity(
    id = id,
    notificationId = notificationId,
    status = status,
    availableAt = availableAt,
    attemptCount = attemptCount,
    leaseOwner = leaseOwner,
    leaseExpiresAt = leaseExpiresAt,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
)
