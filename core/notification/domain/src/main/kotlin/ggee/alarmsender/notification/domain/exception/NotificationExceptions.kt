package ggee.alarmsender.notification.domain.exception

/**
 * 알림이 존재하지 않을 때 발생. 의미가 정확히 일치하는 JDK 표준 예외(NoSuchElementException) 를 상속한다.
 * `code` 프로퍼티는 API 응답에서 사용하는 안정적인 식별자.
 */
class NotificationNotFoundException(
    val notificationId: Long,
) : NoSuchElementException("알림(id=$notificationId)을 찾을 수 없습니다") {
    val code: String = "NOTIFICATION_NOT_FOUND"
}

/**
 * 본인 외 사용자가 알림에 접근하려 할 때 발생.
 */
class NotificationAccessDeniedException(
    val notificationId: Long,
    val requesterId: String,
) : IllegalStateException("사용자($requesterId)는 알림(id=$notificationId)에 접근 권한이 없습니다") {
    val code: String = "NOTIFICATION_ACCESS_DENIED"
}
