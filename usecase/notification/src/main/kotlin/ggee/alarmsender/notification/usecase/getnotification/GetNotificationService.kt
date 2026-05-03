package ggee.alarmsender.notification.usecase.getnotification

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.NotificationRepository
import ggee.alarmsender.notification.usecase.getnotification.GetNotificationQuery
import ggee.alarmsender.notification.usecase.getnotification.GetNotificationUseCase
import ggee.alarmsender.notification.usecase.getnotification.NotificationAccessDeniedException
import ggee.alarmsender.notification.usecase.getnotification.NotificationNotFoundException
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
