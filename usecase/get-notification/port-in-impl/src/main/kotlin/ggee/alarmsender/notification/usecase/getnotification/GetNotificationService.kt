package ggee.alarmsender.notification.usecase.getnotification

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.usecase.getnotification.port.GetNotificationQuery
import ggee.alarmsender.notification.usecase.getnotification.port.GetNotificationUseCase
import ggee.alarmsender.notification.usecase.getnotification.port.NotificationAccessDeniedException
import ggee.alarmsender.notification.usecase.getnotification.port.NotificationNotFoundException
import org.springframework.stereotype.Service

@Service
class GetNotificationService(
    private val repository: NotificationRepository,
) : GetNotificationUseCase {

    override fun execute(query: GetNotificationQuery): Notification {
        val notification = repository.findById(query.notificationId)
            ?: throw NotificationNotFoundException(query.notificationId)
        if (notification.recipientId != query.requesterId) {
            throw NotificationAccessDeniedException(query.notificationId, query.requesterId)
        }
        return notification
    }
}
