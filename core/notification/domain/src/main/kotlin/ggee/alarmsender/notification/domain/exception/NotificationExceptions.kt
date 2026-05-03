package ggee.alarmsender.notification.domain.exception

import ggee.alarmsender.notification.domain.NotificationChannel
import ggee.alarmsender.notification.domain.NotificationType

/**
 * 알림이 존재하지 않을 때 발생. 의미가 정확히 일치하는 JDK 표준 예외(NoSuchElementException) 를 상속한다.
 * `code` 프로퍼티는 API 응답에서 사용하는 안정적인 식별자.
 */
class NotificationNotFoundException(
    val notificationId: Long,
) : NoSuchElementException("알림(id=$notificationId)을 찾을 수 없습니다") {
    val code: String = "NOTIFICATION_NOT_FOUND"
}

class NotificationTemplateNotFoundException(
    val type: NotificationType,
    val channel: NotificationChannel,
) : NoSuchElementException("알림 템플릿(type=$type, channel=$channel)을 찾을 수 없습니다") {
    val code: String = "NOTIFICATION_TEMPLATE_NOT_FOUND"
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

/**
 * 본인 외 사용자가 다른 수신자의 알림 목록을 조회하려 할 때 발생.
 * 단일 알림이 아닌 자원(목록) 단위 권한 거부에 사용.
 */
class RecipientForbiddenException(
    val targetRecipientId: String,
    val requesterId: String,
) : IllegalStateException("사용자($requesterId)는 ($targetRecipientId)의 알림 목록에 접근 권한이 없습니다") {
    val code: String = "RECIPIENT_FORBIDDEN"
}

/**
 * outbox row 와 notification row 의 일대일 관계가 깨졌을 때 발생.
 * 정상 흐름에서는 발생하지 않으며 데이터 불일치(수동 DB 조작 등) 신호다.
 */
class NotificationDataInconsistencyException(
    message: String,
) : IllegalStateException(message) {
    val code: String = "NOTIFICATION_DATA_INCONSISTENCY"
}

/**
 * OPERATOR 권한이 필요한 작업을 일반 USER 가 시도했을 때 발생.
 * (예: DEAD_LETTER 수동 재시도)
 */
class OperatorOnlyException(
    val operationName: String,
    val requesterId: String,
) : IllegalStateException("'$operationName' 은(는) 운영자만 수행할 수 있습니다 (요청자: $requesterId)") {
    val code: String = "OPERATOR_ONLY"
}
