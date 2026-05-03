package ggee.alarmsender.notification.bootstrap.api

import ggee.alarmsender.library.exception.BusinessBaseException
import ggee.alarmsender.notification.domain.exception.NotificationAccessDeniedException
import ggee.alarmsender.notification.domain.exception.NotificationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * 모든 비즈니스 예외(BusinessBaseException 서브타입)를 단일 진입점에서 처리한다.
     * HTTP 상태 매핑은 도메인 관심사 밖이므로 web 어댑터(여기) 가 책임진다.
     */
    @ExceptionHandler(BusinessBaseException::class)
    fun handleBusinessException(e: BusinessBaseException): ResponseEntity<ErrorResponse> {
        val status = mapToHttpStatus(e)
        return ResponseEntity.status(status).body(ErrorResponse(code = e.code, message = e.message))
    }

    private fun mapToHttpStatus(e: BusinessBaseException): HttpStatus = when (e) {
        is NotificationNotFoundException -> HttpStatus.NOT_FOUND
        is NotificationAccessDeniedException -> HttpStatus.FORBIDDEN
        else -> HttpStatus.BAD_REQUEST // 기본 비즈니스 위반 — 서브타입이 늘어나면 매핑 추가
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidArgument(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("VALIDATION_FAILED", msg))
    }

    /**
     * Kotlin require / IllegalArgumentException — Command DTO init 블록 등 저수준 검증 용도.
     * 비즈니스 의미가 있는 예외는 BusinessBaseException 서브타입으로 표현해야 한다.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", e.message ?: ""))

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse("INTERNAL_ERROR", e.message ?: "internal error"))
    }
}
