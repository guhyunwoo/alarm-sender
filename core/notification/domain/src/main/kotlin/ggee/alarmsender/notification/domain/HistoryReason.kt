package ggee.alarmsender.notification.domain

enum class HistoryReason {
    CREATED,
    CLAIMED,
    SENT,
    TRANSIENT_FAILURE,
    /**
     * 재시도 한도 소진(`attemptCount >= maxAttempts`) 으로 자연 격리.
     * 실제 시도 횟수가 한도까지 채워진 정상 흐름.
     */
    EXHAUSTED,
    /**
     * 영구 실패(`EmailSendResult.PermanentFailure`) 로 첫 시도에서 즉시 격리.
     * SMTP 4xx, 잘못된 주소 등 재시도 의미가 없는 경우.
     */
    PERMANENT_FAILURE,
    RECLAIMED,
    MANUAL_RETRY,
    READ,
}
