package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationDataInconsistencyException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import ggee.alarmsender.notification.domain.exception.NotificationTemplateNotFoundException
import ggee.alarmsender.notification.domain.exception.OperatorOnlyException
import ggee.alarmsender.notification.domain.exception.RecipientForbiddenException
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotificationNotFoundException::class)
    fun notFound(e: NotificationNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.code, e.message ?: ""))

    @ExceptionHandler(NotificationTemplateNotFoundException::class)
    fun templateNotFound(e: NotificationTemplateNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.code, e.message ?: ""))

    @ExceptionHandler(NotificationAccessDeniedException::class)
    fun accessDenied(e: NotificationAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(e.code, e.message ?: ""))

    @ExceptionHandler(RecipientForbiddenException::class)
    fun recipientForbidden(e: RecipientForbiddenException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(e.code, e.message ?: ""))

    @ExceptionHandler(OperatorOnlyException::class)
    fun operatorOnly(e: OperatorOnlyException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(e.code, e.message ?: ""))

    /**
     * outbox ↔ notification 매핑 깨짐 같은 데이터 불일치는 클라이언트 잘못이 아니라 500.
     * 다만 메시지에 내부 ID 가 들어가 있어 운영자가 추적 가능하므로 그대로 노출한다.
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

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingRequestHeaderException::class,
    )
    fun requestBindingFailure(e: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("BAD_REQUEST", e.message ?: "요청 형식이 올바르지 않습니다"))

    /**
     * Kotlin require / IllegalArgumentException — Command DTO init 블록 같은 저수준 검증.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", e.message ?: ""))

    /**
     * 도메인 상태 전제 위반은 IllegalStateException — 예: DEAD_LETTER 가 아닌 알림에 재시도 호출.
     * "지금 상태에서는 못 한다" 는 뜻이라 409 Conflict 가 맞다.
     *
     * 권한 거부(403) 도메인 예외는 [SecurityException] 계열로 분리되어 있어 여기로 흘러오지 않는다.
     * 새 IllegalStateException 도메인 예외를 추가할 때도 의미가 "상태 전제 위반"이 맞는지 검토하고,
     * 다른 의미라면 별도 부모/핸들러로 분리한다.
     */
    @ExceptionHandler(IllegalStateException::class)
    fun illegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("INVALID_STATE", e.message ?: ""))

    /**
     * 권한 거부 fallback. 명시 핸들러가 없는 SecurityException 이 흘러와도 500 이 아니라 403 으로 간다.
     * (구체적인 도메인 권한 예외는 위에서 각각 처리.)
     */
    @ExceptionHandler(SecurityException::class)
    fun securityFallback(e: SecurityException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", e.message ?: ""))

    /**
     * @Version 으로 stale write 차단. lease 만료 reclaim 후 죽은 줄 알았던 워커가 살아나는 경우 등.
     * 클라이언트는 그냥 retry 하면 풀린다.
     */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun optimisticLock(e: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
        log.warn("optimistic lock conflict (다른 트랜잭션이 같은 row 를 먼저 갱신함)", e)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("OPTIMISTIC_LOCK_CONFLICT", "다른 처리와 충돌이 발생했습니다. 잠시 후 다시 시도해 주세요."))
    }

    /**
     * 마지막 안전망. 응답 body 에는 내부 메시지를 노출하지 않는다 (스택 / SQL / 경로 누출 방지).
     * 디버깅은 서버 로그 (이미 ERROR 로 stack 찍힘) 와 trace 로.
     */
    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "내부 오류가 발생했습니다. 관리자에게 문의해 주세요."))
    }
}
