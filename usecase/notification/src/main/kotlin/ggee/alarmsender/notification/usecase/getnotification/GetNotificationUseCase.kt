package ggee.alarmsender.notification.usecase.getnotification

import ggee.alarmsender.notification.domain.Notification

interface GetNotificationUseCase {
    /**
     * 단건 조회. 본인이 아닌 사용자가 조회 시 [NotificationAccessDeniedException].
     * 존재하지 않으면 [NotificationNotFoundException].
     */
    fun execute(query: GetNotificationQuery): Notification
}

data class GetNotificationQuery(
    val notificationId: Long,
    val requesterId: String,
)

class NotificationNotFoundException(val notificationId: Long) : RuntimeException("notification(id=$notificationId)")
class NotificationAccessDeniedException(val notificationId: Long, val requesterId: String) : RuntimeException("notification(id=$notificationId) is not owned by $requesterId")
