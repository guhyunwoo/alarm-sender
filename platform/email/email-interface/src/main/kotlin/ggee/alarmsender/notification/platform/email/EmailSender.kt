package ggee.alarmsender.notification.platform.email

interface EmailSender {
    fun send(request: EmailRequest): EmailSendResult
}

data class EmailRequest(
    val to: String,
    val subject: String,
    val body: String,
)

/**
 * 발송 결과.
 *  - [Success]            : 정상 발송. outbox DONE, notification SENT.
 *  - [TransientFailure]   : 일시 장애 (네트워크, SMTP 5xx). 백오프 후 재시도 가능.
 *  - [PermanentFailure]   : 영구 실패 (잘못된 주소, 차단된 도메인, SMTP 4xx 등).
 *                            재시도 의미 없음 → 즉시 DEAD_LETTER 격리.
 */
sealed class EmailSendResult {
    data object Success : EmailSendResult()
    data class TransientFailure(val reason: String) : EmailSendResult()
    data class PermanentFailure(val reason: String) : EmailSendResult()
}
