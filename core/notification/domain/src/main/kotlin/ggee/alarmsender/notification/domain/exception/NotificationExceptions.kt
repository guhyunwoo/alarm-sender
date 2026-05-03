package ggee.alarmsender.notification.domain.exception

import ggee.alarmsender.library.exception.BusinessBaseException

class NotificationNotFoundException(
    val notificationId: Long,
) : BusinessBaseException(
    code = "NOTIFICATION_NOT_FOUND",
    message = "알림(id=$notificationId)을 찾을 수 없습니다",
)

class NotificationAccessDeniedException(
    val notificationId: Long,
    val requesterId: String,
) : BusinessBaseException(
    code = "NOTIFICATION_ACCESS_DENIED",
    message = "사용자($requesterId)는 알림(id=$notificationId)에 접근 권한이 없습니다",
)
