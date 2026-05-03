ALTER TABLE notification
    ADD COLUMN scheduled_at TIMESTAMPTZ;

CREATE TABLE notification_template (
    id               BIGSERIAL PRIMARY KEY,
    type             VARCHAR(64) NOT NULL,
    channel          VARCHAR(32) NOT NULL,
    subject_template TEXT        NOT NULL,
    body_template    TEXT        NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_notification_template_type_channel UNIQUE (type, channel)
);

INSERT INTO notification_template (type, channel, subject_template, body_template, updated_at)
VALUES
    ('ENROLL_COMPLETED', 'EMAIL', '수강 신청 완료: {{courseName}}', '{{recipientName}}님, {{courseName}} 수강 신청이 완료되었습니다.', now()),
    ('PAYMENT_CONFIRMED', 'EMAIL', '결제 확정: {{courseName}}', '{{recipientName}}님, {{courseName}} 결제가 확정되었습니다.', now()),
    ('COURSE_STARTING_TOMORROW', 'EMAIL', '내일 시작하는 강의: {{courseName}}', '{{recipientName}}님, {{courseName}} 강의가 내일 시작됩니다.', now()),
    ('ENROLL_CANCELLED', 'EMAIL', '수강 신청 취소: {{courseName}}', '{{recipientName}}님, {{courseName}} 수강 신청이 취소되었습니다.', now()),
    ('ENROLL_COMPLETED', 'IN_APP', '수강 신청 완료', '{{courseName}} 수강 신청이 완료되었습니다.', now()),
    ('PAYMENT_CONFIRMED', 'IN_APP', '결제 확정', '{{courseName}} 결제가 확정되었습니다.', now()),
    ('COURSE_STARTING_TOMORROW', 'IN_APP', '강의 시작 D-1', '{{courseName}} 강의가 내일 시작됩니다.', now()),
    ('ENROLL_CANCELLED', 'IN_APP', '수강 신청 취소', '{{courseName}} 수강 신청이 취소되었습니다.', now());
