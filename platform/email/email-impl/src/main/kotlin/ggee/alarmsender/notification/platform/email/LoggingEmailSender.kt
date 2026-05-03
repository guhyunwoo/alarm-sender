package ggee.alarmsender.notification.platform.email

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 실제 SMTP 연동 전 단계의 로그 Mock 구현.
 * 운영 도입 시 같은 EmailSender 인터페이스를 구현하는 어댑터로 교체된다.
 *
 * `production` profile 에서는 빈 등록을 막아둔다 — 운영에 실수로 들어가면 모든 이메일을
 * silently 삼키고 SENT 처리하는 사고로 직결된다. 운영 profile 에서는 실제 SmtpEmailSender 등
 * 다른 어댑터를 별도 등록해야 한다.
 */
@Component
@Profile("!production")
class LoggingEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override fun send(request: EmailRequest): EmailSendResult {
        log.info("[EMAIL] to={} subject={} body.length={}", request.to, request.subject, request.body.length)
        return EmailSendResult.Success
    }
}
