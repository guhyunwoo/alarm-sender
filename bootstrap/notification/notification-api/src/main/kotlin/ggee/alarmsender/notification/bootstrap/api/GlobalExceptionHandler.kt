package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationDataInconsistencyException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.domain.exception.RecipientForbiddenException
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotificationNotFoundException::class)
    fun notFound(e: NotificationNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.code, e.message ?: ""))

    @ExceptionHandler(NotificationAccessDeniedException::class)
    fun accessDenied(e: NotificationAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(e.code, e.message ?: ""))

    @ExceptionHandler(RecipientForbiddenException::class)
    fun recipientForbidden(e: RecipientForbiddenException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(e.code, e.message ?: ""))

    /**
     * outbox ↔ notification 일대일 관계 깨짐 등 데이터 불일치는 클라이언트 잘못이 아님 → 500.
     * 다만 메시지에 내부 ID 가 들어가 있어 운영자 추적이 가능하므로 그대로 노출.
     */
    @ExceptionHandler(NotificationDataInconsistencyException::class)
    fun dataInconsistency(e: NotificationDataInconsistencyException): ResponseEntity<ErrorResponse> {
        log.error("data inconsistency", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(e.code, e.message ?: ""))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidArgument(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("VALIDATION_FAILED", msg))
    }

    /**
     * Kotlin require / IllegalArgumentException — Command DTO init 블록 등 저수준 검증 용도.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", e.message ?: ""))

    /**
     * 도메인 require 위반 (예: DEAD_LETTER 가 아닌 알림에 수동 재시도 시도) 은 IllegalStateException.
     * 의미상 "현재 상태에서 동작 불가" → 409 Conflict 가 정확.
     */
    @ExceptionHandler(IllegalStateException::class)
    fun illegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("INVALID_STATE", e.message ?: ""))

    /**
     * @Version 으로 인한 stale write 차단. lease 만료 reclaim 후 죽은 워커 부활 등.
     * 클라이언트 입장에서는 retry 하면 해결됨.
     */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun optimisticLock(e: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
        log.warn("optimistic lock conflict (다른 트랜잭션이 row 를 갱신)", e)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("OPTIMISTIC_LOCK_CONFLICT", "다른 처리와 충돌이 발생했습니다. 잠시 후 다시 시도해 주세요."))
    }

    /**
     * 마지막 안전망. 응답 body 에는 내부 메시지를 노출하지 않는다 (스택 / SQL / 경로 누출 방지).
     * 디버깅은 서버 로그(이미 ERROR 로 stack 출력) 와 trace 로 진행.
     */
    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "내부 오류가 발생했습니다. 관리자에게 문의해 주세요."))
    }
}
