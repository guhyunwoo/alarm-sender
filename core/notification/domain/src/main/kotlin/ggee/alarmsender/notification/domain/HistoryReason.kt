package ggee.alarmsender.notification.domain

enum class HistoryReason {
    CREATED,
    CLAIMED,
    SENT,
    TRANSIENT_FAILURE,
    EXHAUSTED,
    RECLAIMED,
    MANUAL_RETRY,
    READ,
}
