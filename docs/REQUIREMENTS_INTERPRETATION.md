# 요구사항 해석 및 가정

이 문서는 과제 명세에서 모호한 부분을 어떻게 해석했는지, 그리고 그 해석이 시스템 설계에 어떻게 반영되었는지를 정리한 문서입니다.

---

## 1. "동일 이벤트"의 정의

해석: 두 축으로 정의했습니다.

1. 클라이언트 입력 단위. `Idempotency-Key` 헤더가 같으면 동일 이벤트로 봅니다.
    - 클라이언트가 네트워크 단절이나 타임아웃 등으로 같은 요청을 두 번 보낼 때 방어합니다.
2. 서버 측 비즈니스 단위. `(recipient_id, type, ref_type, ref_id)`가 같으면 동일 이벤트로 봅니다.
    - 서로 다른 호출자(예: 결제 서비스와 수강 신청 서비스)가 같은 이벤트로 들어오는 경우를 방어합니다.

두 축 모두 DB UNIQUE 제약(부분 인덱스)으로 보호합니다.

개선 제안: 자연 키만으로 충분한 도메인이라면 `Idempotency-Key`가 불필요하지만, 운영 단계에서 *동일 이벤트의 정의를 누가 책임지는가*의 경계가 약해집니다. 명세에 둘 다 두면 책임이 명확해집니다.

---

## 2. "비동기"의 범위

해석: API 트랜잭션과 발송 워커를 별 프로세스로 분리했습니다.

- API는 알림과 Outbox INSERT까지만 수행하고 즉시 ACK합니다 (PENDING).
- 실제 발송은 `notification-worker` 프로세스가 폴링해서 처리합니다.
- 클라이언트는 `GET /api/v1/notifications/{id}`로 상태를 조회할 수 있습니다.

개선 제안: 클라이언트가 "발송이 끝났다" 시점을 알아야 하는 비즈니스가 추가된다면 push 채널(SSE / WebHook) 도입이 필요합니다. 본 시스템은 polling을 가정하고 있습니다.

---

## 3. "실제 메시지 브로커 없이 운영 전환 가능"

해석: Outbox 패턴을 채택했습니다. 추후 Kafka/SQS 도입 시 어댑터만 교체하면 됩니다.

- `NotificationOutboxRepository` 인터페이스 + DB 폴링 구현 (현재).
- Kafka 도입 시 `OutboxRelay`(별도 컴포넌트)가 outbox의 PENDING row를 Kafka 토픽으로 publish하고, 워커는 토픽 컨슈머로 변환합니다. 도메인·유즈케이스 코드 변경은 없습니다.

개선 제안: 부하가 일정 임계 이상(~수천 TPS)에 도달하기 전까지는 폴링만으로도 충분합니다. 도입 결정은 측정 후에 내리는 것이 적절합니다.

---

## 4. "다중 인스턴스 중복 방지"

해석: PostgreSQL `SELECT … FOR UPDATE SKIP LOCKED` + lease 패턴을 사용했습니다.

- 다중 워커가 동시에 폴링해도 같은 row를 잡지 않습니다. 단일 SQL 안에서 잠금과 UPDATE가 원자적으로 처리됩니다.
- 락 대기 없이 다음 row로 건너뛰기 때문에 throughput을 유지할 수 있습니다.
- 처리 중 IN_PROGRESS 마킹과 lease 시각으로 다른 워커가 같은 row를 다시 잡지 못하도록 막습니다.

개선 제안: PostgreSQL 외 DB로 이전할 때 SKIP LOCKED 미지원이면 분산 락(Redisson 등)으로 대체해야 합니다. 본 구현은 PG 의존을 명시적으로 채택했습니다.

---

## 5. "처리 중 상태가 일정 시간 이상 지속되면 다른 워커가 처리하도록"

해석: `IN_PROGRESS`이면서 `lease_expires_at < now()`인 row를 배치가 `PENDING`으로 reclaim합니다.

- lease 기본 30s, 배치 5s 주기로 만료 row를 조회합니다.
- "워커가 죽었는지" 직접 확인하지는 않습니다. lease 만료 시각만 보고 결정합니다.
- 워커가 다시 살아나서 같은 row를 IN_PROGRESS인 줄 알고 처리하려고 하면, 도메인 객체의 `claim()`이 PENDING만 허용하므로 race가 발생해도 두 번째 워커는 그대로 실패합니다. 다음 폴링에서 정상 처리됩니다.

개선 제안: lease 갱신(heartbeat)을 도입하면 워커가 살아있을 때 lease를 연장해 잘못된 reclaim을 줄일 수 있습니다. 본 구현은 단순함을 위해 연장은 지원하지 않습니다.

---

## 6. "서버 재시작 후에도 처리 중인 알림이 유실되지 않아야"

해석: 모든 처리 상태가 DB에 영속화되기 때문에 재기동 시 자동으로 재개됩니다.

- claim 후 워커가 죽어도 outbox는 IN_PROGRESS와 lease 시각이 set된 상태로 DB에 남아 있습니다.
- 재기동된 워커(같은 worker든 다른 worker든)가 lease 만료 후 reclaim받아 재처리합니다.

검증: 통합 테스트에서 워커 재시작 시나리오는 lease 만료 reclaim 테스트로 동일한 효과를 확인했습니다.

---

## 7. "예외를 단순히 무시하지 않고, 재시도 한도가 초과하면 별도로 표시"

해석:

- 모든 일시 실패는 `notification_history`에 `TRANSIENT_FAILURE` reason으로 적재하고 outbox의 `last_error`를 갱신합니다.
- 재시도 한도(5회) 초과 시 `outbox.status = DEAD`, `notification.status = DEAD_LETTER`로 격리합니다. history에는 `EXHAUSTED`를 적재합니다.
- 운영자는 DEAD_LETTER 중 적절한 것만 선택해서 수동 재시도합니다 (`attempt_count = 0`으로 리셋, history `MANUAL_RETRY`).

개선 제안: 영구 실패(예: 잘못된 이메일 포맷)와 일시 실패(SMTP 5xx)를 EmailSender 레벨에서 분리 반환하면 영구 실패는 DEAD 격리를 즉시 처리할 수 있습니다. 본 구현은 모든 실패를 Transient로 취급한 뒤 한도 초과 시 격리하는 방식입니다. 보수적이지만 발송 시스템 안정성을 우선했습니다.

---

## 8. "수동 재시도 시 attempt_count 정책"

해석: DEAD_LETTER → PENDING으로 복귀할 때 `attempt_count = 0`으로 초기화합니다.

- 운영자의 명시적 의지로 새 시도를 시작한다는 의미입니다.
- `notification_history`에 `MANUAL_RETRY` 사유를 남겨 자동 재시도와 구분합니다.

대안: attempt_count를 그대로 유지하면 한 번 더 실패했을 때 즉시 DEAD로 가게 됩니다 (수동 재시도의 의미가 없어집니다). 0으로 리셋하는 쪽이 자연스럽다고 판단했습니다.

---

## 9. "읽음 처리"

해석:

- `read_at IS NULL`일 때만 `now()`로 set, 이후 호출은 no-op으로 처리합니다.
- 멀티 디바이스에서 동시에 read 호출이 들어와도 가장 먼저 도착한 호출의 시각이 유지됩니다 (이미 set된 read_at을 덮어쓰지 않습니다).
- 본인이 아닌 사용자의 read 시도는 403으로 차단합니다.

구현: 도메인 객체의 `markAsRead()`가 객체 단위 멱등성을 보장하고, 실제 동시성은 DB 조건부 갱신(`read_at IS NULL`)으로 보장합니다.

---

## 10. "발송 스케줄링"

해석: 알림 요청에 `scheduled_at`이 있으면 해당 시각부터 발송 대상이 됩니다.

- `notification.scheduled_at`에 예약 시각을 보관합니다.
- `notification_outbox.available_at`을 예약 시각으로 설정합니다.
- 워커는 기존 조건 `status=PENDING AND available_at <= now`로만 claim하기 때문에, 별도 예약 전용 스케줄러가 필요하지 않습니다.

---

## 11. "알림 템플릿 관리"

해석: 타입·채널별 템플릿을 DB에서 관리하고, 운영자가 API로 수정합니다.

- 키: `(type, channel)`
- 값: `subject_template`, `body_template`
- 렌더링: `{{key}}` placeholder를 notification payload 값으로 치환합니다.
- 운영자만 수정 가능하고 일반 사용자는 조회만 가능합니다.

---

## 12. 인증 / 인가

해석 및 한계:

- `X-User-Id` 헤더로 단순 식별. 과제 허용 범위에서 채택했습니다.
- 운영 환경에서는 JWT / OAuth2 + 게이트웨이 검증으로 교체가 필요합니다.
- 본인 외 사용자의 알림 조회·읽기는 컨트롤러 레벨에서 차단합니다.

---

## 13. 정리: 본 시스템이 명시적으로 답하는 질문

| 질문 | 답 | 근거 |
|---|---|---|
| 같은 이벤트가 두 번 들어오면? | 첫 알림 그대로 반환 | 2단 멱등성 (HTTP + 자연 키) |
| 워커가 동시에 두 개 떠 있으면? | 같은 row를 두 번 잡지 않음 | SKIP LOCKED |
| 워커가 발송 중 죽으면? | lease 만료 후 다른 워커/배치가 reclaim | lease 시각 + reclaim 배치 |
| 외부 호출이 일시 실패하면? | 지수 백오프로 자동 재시도 | 1m → 2m → 4m → 8m → 16m, 최대 5회 |
| 재시도 한도 초과되면? | DEAD_LETTER 격리, 운영자 가시화 | history EXHAUSTED + 수동 재시도 API |
| 서버 재시작되면? | 미처리 outbox 자동 재개 | 모든 상태 DB 영속, lease 만료 시 reclaim |
| 멀티 디바이스에서 동시에 read 누르면? | read_at 한 번만 set | 도메인 객체 멱등성 |
| 특정 시각에 발송해야 하면? | outbox.available_at까지 worker claim 지연 | scheduled_at → available_at |
| 메시지 문구를 바꾸려면? | 운영자 템플릿 수정 API | notification_template |
