-- 낙관적 락용 version 컬럼 추가.
-- lease 만료 reclaim 과 원 워커 stale write 사이의 race 를 막기 위함.
ALTER TABLE notification_outbox
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
