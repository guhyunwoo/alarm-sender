package ggee.alarmsender.notification.platform.email

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 실제 SMTP 연동 전 단계의 로그 Mock 구현.
 * 운영 도입 시 같은 EmailSender 인터페이스를 구현하는 어댑터로 교체된다.
 */
@Component
class LoggingEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override fun send(request: EmailRequest): EmailSendResult {
        log.info("[EMAIL] to={} subject={} body.length={}", request.to, request.subject, request.body.length)
        return EmailSendResult.Success
    }
}
