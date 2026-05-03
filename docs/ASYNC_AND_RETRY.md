# 비동기 처리 구조 및 재시도 정책

---

## 1. 전체 흐름

```
[Client]
   │ POST /api/v1/notifications  (Idempotency-Key 헤더)
   ▼
[notification-api]
   │ SendNotificationUseCase
   │   ┌── 단일 트랜잭션 ──┐
   │   │ INSERT notification │
   │   │ INSERT notification_outbox (status=PENDING, available_at=scheduled_at 또는 now)
   │   │ INSERT notification_history (CREATED)
   │   └────────────────────┘
   │ 201 Created (PENDING) 즉시 ACK
   ▼
[notification-worker (다중 인스턴스 가능)]
   │ @Scheduled (fixedDelay 1s)
   │ DispatchNotificationUseCase.execute()
   │   1) claimBatch — UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING *
   │   2) for each row: 외부 발송 호출 (트랜잭션 밖)
   │   3) 결과 반영 — row 단위 새 트랜잭션
   │        SUCCESS → 템플릿 렌더링 + outbox DONE + notification SENT + history SENT
   │        TRANSIENT → outbox PENDING(available_at = now+backoff) + history TRANSIENT_FAILURE
   │        EXHAUSTED → outbox DEAD + notification DEAD_LETTER + history EXHAUSTED
   ▼
[notification-batch]
   │ @Scheduled (fixedDelay 5s)
   │ ReclaimNotificationUseCase.execute()
   │   IN_PROGRESS AND lease_expires_at < now → PENDING 으로 reclaim + history RECLAIMED
   ▼
[Client] GET / read API 로 결과 확인
```

---

## 2. 상태 전이

### 2.1 `notification`

```
PENDING ──dispatch success──▶ SENT
   │
   └─ exceed max attempts / permanent failure ─▶ DEAD_LETTER ──manual retry──▶ PENDING
```

> 워커 처리 중간 상태는 outbox 의 PENDING/IN_PROGRESS/DONE/DEAD 로만 표현한다. 사용자 가시 상태인 notification 은 PENDING/SENT/DEAD_LETTER 만 가진다.

### 2.2 `notification_outbox`

| 현재 상태 | 트리거 | 다음 상태 | 동반 동작 |
|---|---|---|---|
| PENDING | worker claim | IN_PROGRESS | lease_owner / lease_expires_at set, available_at 무효화 |
| IN_PROGRESS | 발송 성공 | DONE | lease 비움, notification SENT, history SENT |
| IN_PROGRESS | 일시 실패 (한도 미만) | PENDING | attempt_count++, available_at = now+backoff(attempt_count) |
| IN_PROGRESS | 일시 실패 (한도 초과) | DEAD | notification DEAD_LETTER, history EXHAUSTED |
| IN_PROGRESS | lease 만료 (배치 reclaim) | PENDING | lease 비움, history RECLAIMED |
| DEAD | 운영자 수동 재시도 | PENDING | attempt_count = 0, last_error 비움, history MANUAL_RETRY |

상태 전이는 `NotificationOutbox` 도메인 객체 안에서 require 로 보호되어 위반 시 IllegalArgumentException.

---

## 3. 재시도 백오프

`ExponentialBackoffRetryPolicy(maxAttempts=5, baseDelay=1m)`

| attemptCount | 다음 시도 대기 |
|---|---|
| 1 | 1m |
| 2 | 2m |
| 3 | 4m |
| 4 | 8m |
| 5 | 16m |
| 6+ | DEAD 격리 |

`RetryPolicy` 는 도메인 인터페이스이므로 정책 변경 시 빈 교체만으로 가능.

---

## 4. 예약 발송

발송 예약은 별도 큐나 스케줄러를 추가하지 않고 outbox 의 `available_at` 으로 표현한다.

| 요청 | notification.scheduled_at | outbox.available_at | 워커 동작 |
|---|---|---|---|
| `scheduled_at` 없음 | NULL | now | 즉시 claim 대상 |
| `scheduled_at` 있음 | 요청 시각 | 요청 시각 | `available_at <= now` 가 될 때까지 대기 |

기존 워커의 polling 조건이 그대로 예약 발송 조건이 되므로, 예약 기능이 재시도/lease 복구 구조와 충돌하지 않는다.

---

## 5. 템플릿 렌더링

`notification_template` 은 `(type, channel)` 별 subject/body template 을 가진다. EMAIL 발송 시 `{{course_name}}`, `{{recipient_name}}` 같은 placeholder 를 notification payload 값으로 치환한다.

템플릿 수정은 운영자 전용 API (`PUT /api/v1/notification-templates/{type}/{channel}`) 로만 가능하다. 템플릿이 없으면 기존 payload 의 `title`/`body` fallback 을 사용해 발송 흐름 자체는 멈추지 않는다.

---

## 6. 트랜잭션 경계 (`AGENTS.md` 트랜잭션 규칙 준수)

| 단계 | 트랜잭션 | 이유 |
|---|---|---|
| API: notification + outbox + history INSERT | 단일 @Transactional | 3 write 의 all-or-nothing 보장 |
| Worker: claimBatch (UPDATE … FOR UPDATE SKIP LOCKED RETURNING) | 단일 SQL 자체 원자 | 별도 @Transactional 불필요 |
| Worker: 외부 발송 호출 | **트랜잭션 밖** | 외부 호출이 트랜잭션을 점유하면 DB 커넥션을 길게 잡음 |
| Worker: 결과 반영 (outbox 갱신 + history) | row 단위 새 트랜잭션 (TransactionTemplate) | 한 row 실패가 같은 배치의 다른 row 를 롤백시키지 않도록 |
| Batch: reclaim 일괄 처리 | 단일 @Transactional | history 적재와 outbox 갱신의 원자성 |

---

## 7. 다중 워커 안전성

- `SELECT … FOR UPDATE SKIP LOCKED` (PostgreSQL) — 동시 폴링 시 같은 row 잠긴 상태에서 락 대기 없이 다음 row 로 건너뛴다.
- `lease_owner` + `lease_expires_at` — 워커가 사라져도 만료 시각 도래 시 다른 워커/배치가 PENDING 으로 reclaim.
- 분산 락 없음. SKIP LOCKED + lease 가 충분.

검증: `core/notification/data/.../NotificationOutboxRepositoryImplTest.kt` 의 `다중 워커 동시 claim — 같은 row 를 두 번 잡지 않는다 (SKIP LOCKED)` 테스트가 8 worker × limit 20 × row 100 시나리오로 중복 미발생을 확인한다.

---

## 8. 멱등성

| 방어선 | 위치 | 동작 |
|---|---|---|
| `Idempotency-Key` 헤더 | API | 같은 키로 재시도 시 use case 단계에서 기존 알림 그대로 반환 (deduplicated=true) |
| `notification.idempotency_key` UNIQUE (partial) | DB | 동시 race 시 마지막 보호선. NULL 은 다중 허용 |
| `(recipient_id, type, ref_type, ref_id)` UNIQUE (partial) | DB | 같은 자연 키 중복 적재 방지. ref 가 NULL 인 경우 미적용 |
| 컨트롤러 충돌 fallback | API | DataIntegrityViolation 발생 시 use case 한 번 더 호출해 기존 row 반환 |

검증:
- `SendNotificationServiceTest`: idempotencyKey / 자연 키 둘 다 멱등 검증
- `NotificationRepositoryImplTest`: DB UNIQUE 제약 동작 검증
- `NotificationControllerTest`: HTTP 레이어에서 두 번째 호출 200 OK + 같은 id 반환 검증

---

## 9. 운영 시나리오 매트릭스

| 시나리오 | 보호 메커니즘 | 검증 테스트 |
|---|---|---|
| 같은 키 재요청 | 2단 멱등성 (헤더 + 자연 키) | SendNotificationServiceTest, NotificationControllerTest |
| 다중 워커 동시 발송 | SKIP LOCKED | NotificationOutboxRepositoryImplTest |
| 워커 죽음 / GC pause | lease 만료 reclaim 배치 | NotificationOutboxRepositoryImplTest, ReclaimNotificationServiceTest |
| 일시적 외부 장애 | 지수 백오프 + attempt 누적 | DispatchNotificationServiceTest |
| 영구적 실패 | 한도 초과 시 DEAD_LETTER 격리 | DispatchNotificationServiceTest |
| 서버 재시작 | DB 영속화 → 재기동 후 자동 재개 | (런타임 시나리오, 자동 재개는 워커 부팅이 곧 검증) |
| 멀티 디바이스 동시 read | read_at 한 번만 set | ReadNotificationServiceTest, NotificationControllerTest |
| 예약 발송 | outbox.available_at 이 scheduled_at 이후일 때만 claim | SendNotificationServiceTest, NotificationControllerTest |
| 템플릿 관리/렌더링 | DB 템플릿 + payload placeholder 치환 | ManageNotificationTemplateServiceTest, DispatchNotificationServiceTest |
