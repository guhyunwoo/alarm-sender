package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.Notification
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

internal fun NotificationEntity.toDomain(objectMapper: ObjectMapper): Notification = Notification(
    id = id,
    recipientId = recipientId,
    type = type,
    channel = channel,
    payload = objectMapper.readValue(payload),
    idempotencyKey = idempotencyKey,
    refType = refType,
    refId = refId,
    status = status,
    readAt = readAt,
    createdAt = createdAt,
    sentAt = sentAt,
)

internal fun Notification.toEntity(objectMapper: ObjectMapper): NotificationEntity = NotificationEntity(
    id = id,
    recipientId = recipientId,
    type = type,
    channel = channel,
    payload = objectMapper.writeValueAsString(payload),
    idempotencyKey = idempotencyKey,
    refType = refType,
    refId = refId,
    status = status,
    readAt = readAt,
    createdAt = createdAt,
    sentAt = sentAt,
)
