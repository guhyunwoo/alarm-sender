-- 알림 본체
CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    VARCHAR(64)  NOT NULL,
    type            VARCHAR(64)  NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    payload         TEXT         NOT NULL,
    idempotency_key VARCHAR(128),
    ref_type        VARCHAR(64),
    ref_id          VARCHAR(64),
    status          VARCHAR(32)  NOT NULL,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ
);

-- 멱등성 1차 방어 (클라이언트 재시도): NULL 허용, 부분 UNIQUE
CREATE UNIQUE INDEX uk_notification_idempotency_key
    ON notification (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 멱등성 2차 방어 (자연 키): ref_type / ref_id 가 모두 NOT NULL 인 경우에만 UNIQUE 강제
CREATE UNIQUE INDEX uk_notification_natural_key
    ON notification (recipient_id, type, ref_type, ref_id)
    WHERE ref_type IS NOT NULL AND ref_id IS NOT NULL;

-- 사용자 알림 목록 조회용
CREATE INDEX idx_notification_recipient_created
    ON notification (recipient_id, created_at DESC);

-- 안 읽은 알림 조회 효율을 위한 partial index
CREATE INDEX idx_notification_recipient_unread
    ON notification (recipient_id, created_at DESC)
    WHERE read_at IS NULL;

-- 비동기 처리 큐
CREATE TABLE notification_outbox (
    id                BIGSERIAL    PRIMARY KEY,
    notification_id   BIGINT       NOT NULL UNIQUE REFERENCES notification (id) ON DELETE CASCADE,
    status            VARCHAR(32)  NOT NULL,
    available_at      TIMESTAMPTZ  NOT NULL,
    attempt_count     INT          NOT NULL DEFAULT 0,
    lease_owner       VARCHAR(128),
    lease_expires_at  TIMESTAMPTZ,
    last_error        TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);

-- SKIP LOCKED 폴링 효율
CREATE INDEX idx_outbox_polling
    ON notification_outbox (available_at)
    WHERE status = 'PENDING';

-- lease 만료 reclaim 효율
CREATE INDEX idx_outbox_lease_expiry
    ON notification_outbox (lease_expires_at)
    WHERE status = 'IN_PROGRESS';

-- 상태 전이 이력 (감사·디버깅)
CREATE TABLE notification_history (
    id              BIGSERIAL    PRIMARY KEY,
    notification_id BIGINT       NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    from_status     VARCHAR(32),
    to_status       VARCHAR(32)  NOT NULL,
    reason          VARCHAR(64)  NOT NULL,
    detail          TEXT,
    occurred_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_history_notification ON notification_history (notification_id, occurred_at);
