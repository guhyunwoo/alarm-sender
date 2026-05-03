package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.notification.domain.exception.RecipientForbiddenException
import ggee.alarmsender.notification.usecase.getnotification.GetNotificationQuery
import ggee.alarmsender.notification.usecase.getnotification.GetNotificationUseCase
import ggee.alarmsender.notification.usecase.listnotifications.ListNotificationsQuery
import ggee.alarmsender.notification.usecase.listnotifications.ListNotificationsUseCase
import ggee.alarmsender.notification.usecase.readnotification.ReadNotificationCommand
import ggee.alarmsender.notification.usecase.readnotification.ReadNotificationUseCase
import ggee.alarmsender.notification.usecase.retrynotification.RetryNotificationCommand
import ggee.alarmsender.notification.usecase.retrynotification.RetryNotificationUseCase
import ggee.alarmsender.notification.usecase.sendnotification.SendNotificationCommand
import ggee.alarmsender.notification.usecase.sendnotification.SendNotificationUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val sendUseCase: SendNotificationUseCase,
    private val listUseCase: ListNotificationsUseCase,
    private val getUseCase: GetNotificationUseCase,
    private val readUseCase: ReadNotificationUseCase,
    private val retryUseCase: RetryNotificationUseCase,
) {

    /**
     * POST /api/v1/notifications
     * Idempotency-Key 헤더로 클라이언트 재시도 안전.
     * 자연 키(recipient + type + ref) 가 중복인 경우에도 기존 알림을 그대로 반환.
     * 동시성 race 처리는 SendNotificationUseCase 내부에서 책임짐 (DB UNIQUE → 재조회).
     */
    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") requesterId: String,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateNotificationRequest,
    ): ResponseEntity<NotificationResponse> {
        val command = SendNotificationCommand(
            recipientId = request.recipientId,
            type = request.type,
            channel = request.channel,
            payload = request.payload,
            idempotencyKey = idempotencyKey,
            refType = request.refType,
            refId = request.refId,
        )
        val result = sendUseCase.execute(command)
        val status = if (result.deduplicated) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(NotificationResponse.from(result.notification))
    }

    @GetMapping("/{id}")
    fun get(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable id: Long,
    ): NotificationResponse = NotificationResponse.from(getUseCase.execute(GetNotificationQuery(id, requesterId)))

    @GetMapping
    fun list(
        @RequestHeader("X-User-Id") requesterId: String,
        @RequestParam(required = false) recipientId: String?,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): List<NotificationResponse> {
        // recipientId 미지정 시 본인 알림 조회
        val targetRecipient = recipientId ?: requesterId
        if (targetRecipient != requesterId) {
            throw RecipientForbiddenException(targetRecipientId = targetRecipient, requesterId = requesterId)
        }

        return listUseCase.execute(ListNotificationsQuery(targetRecipient, unreadOnly, limit, offset))
            .map(NotificationResponse::from)
    }

    @PostMapping("/{id}/read")
    fun read(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable id: Long,
    ): ReadNotificationResponse {
        val r = readUseCase.execute(ReadNotificationCommand(id, requesterId))
        return ReadNotificationResponse(
            id = r.notification.requireId(),
            readAt = r.notification.readAt!!,
            newlyRead = r.newlyRead,
        )
    }

    /**
     * POST /api/v1/notifications/{id}/retry
     * DEAD_LETTER 격리된 알림을 본인 의지로 재시도. attempt_count 가 0 으로 리셋되어 다시 5회까지 자동 재시도된다.
     * DEAD_LETTER 가 아닌 알림에 호출 시 400 (도메인 불변식 위반).
     */
    @PostMapping("/{id}/retry")
    fun retry(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable id: Long,
    ): NotificationResponse =
        NotificationResponse.from(retryUseCase.execute(RetryNotificationCommand(id, requesterId)))
}
