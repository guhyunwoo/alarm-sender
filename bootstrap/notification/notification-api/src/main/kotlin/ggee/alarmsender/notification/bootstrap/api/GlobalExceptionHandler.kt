package ggee.alarmsender.notification.bootstrap.api

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

    @ExceptionHandler(NotificationNotFoundException::class)
    fun notFound(e: NotificationNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.code, e.message ?: ""))

    @ExceptionHandler(NotificationAccessDeniedException::class)
    fun accessDenied(e: NotificationAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(e.code, e.message ?: ""))

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

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse("INTERNAL_ERROR", e.message ?: "internal error"))
    }
}
