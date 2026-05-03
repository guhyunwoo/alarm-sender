package ggee.alarmsender.notification.domain

/**
 * 사용자 가시 상태. 발송 워커의 처리 중간 상태(IN_PROGRESS) 는 [OutboxStatus] 에서 별도 관리한다 —
 * notification.status 는 "최종 사용자가 알림을 어떤 단계로 인식하는가" 만 표현한다.
 *
 *  - PENDING       : 적재됨 / DEAD_LETTER 후 수동 재시도로 복귀
 *  - SENT          : 발송 완료 (외부 채널 전송 + outbox DONE)
 *  - DEAD_LETTER   : 재시도 한도 초과로 격리 — 운영자 수동 재시도 대상
 */
enum class NotificationStatus {
    PENDING,
    SENT,
    DEAD_LETTER,
}
