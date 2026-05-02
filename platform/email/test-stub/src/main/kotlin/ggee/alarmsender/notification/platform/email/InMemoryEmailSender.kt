package ggee.alarmsender.notification.platform.email

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 테스트용 in-memory EmailSender. 발송 시도 기록만 남기고 결과는 외부에서 주입한다.
 */
class InMemoryEmailSender : EmailSender {

    private val sent = CopyOnWriteArrayList<EmailRequest>()

    var nextResult: EmailSendResult = EmailSendResult.Success

    override fun send(request: EmailRequest): EmailSendResult {
        sent.add(request)
        return nextResult
    }

    fun all(): List<EmailRequest> = sent.toList()

    fun clear() {
        sent.clear()
        nextResult = EmailSendResult.Success
    }
}
