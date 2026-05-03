package ggee.alarmsender.notification.usecase.listnotifications

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.usecase.listnotifications.ListNotificationsQuery
import ggee.alarmsender.notification.usecase.listnotifications.ListNotificationsUseCase
import org.springframework.stereotype.Service

@Service
class ListNotificationsService(
    private val repository: NotificationRepository,
) : ListNotificationsUseCase {

    override fun execute(query: ListNotificationsQuery): List<Notification> =
        repository.findByRecipient(
            recipientId = query.recipientId,
            unreadOnly = query.unreadOnly,
            limit = query.limit,
            offset = query.offset,
        )
}
