package ggee.alarmsender.notification.platform.email

interface EmailSender {
    fun send(request: EmailRequest): EmailSendResult
}

data class EmailRequest(
    val to: String,
    val subject: String,
    val body: String,
)

sealed class EmailSendResult {
    data object Success : EmailSendResult()
    data class TransientFailure(val reason: String) : EmailSendResult()
}
