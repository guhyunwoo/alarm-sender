package ggee.alarmsender.notification.data

import ggee.alarmsender.notification.domain.NotificationTemplate

internal fun NotificationTemplateEntity.toDomain(): NotificationTemplate = NotificationTemplate(
    id = id,
    type = type,
    channel = channel,
    subjectTemplate = subjectTemplate,
    bodyTemplate = bodyTemplate,
    updatedAt = updatedAt,
)

internal fun NotificationTemplate.toEntity(): NotificationTemplateEntity = NotificationTemplateEntity(
    id = id,
    type = type,
    channel = channel,
    subjectTemplate = subjectTemplate,
    bodyTemplate = bodyTemplate,
    updatedAt = updatedAt,
)
