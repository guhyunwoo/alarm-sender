# 알림 발송 시스템 (alarm-sender)

라이브클래스 백엔드 셀 채용 과제 C — 알림 발송 시스템 구현 저장소입니다.

---

## 프로젝트 개요

수강 신청 완료, 결제 확정, 강의 시작 D-1, 취소 처리 같은 비즈니스 이벤트가 발생했을 때 사용자에게 EMAIL과 IN_APP 알림을 발송하는 시스템입니다.

과제에서 요구한 4가지 항목은 다음과 같이 풀었습니다.

| 항목 | 구현 |
|---|---|
| 비동기 | API 트랜잭션과 발송 워커를 별 프로세스로 분리. Outbox 패턴으로 큐잉 |
| 멱등성 | `Idempotency-Key` 헤더 + `(recipient, type, ref)` 자연 키, 두 축 모두 UNIQUE 제약 |
| 재시도 | 지수 백오프 (1m → 2m → 4m → 8m → 16m, 최대 5회) + DEAD_LETTER 격리 |
| 운영 | `SELECT ... FOR UPDATE SKIP LOCKED` 폴링, lease 만료 reclaim, 재시작 무손실 |

비즈니스 트랜잭션과 알림 발송을 분리하고, 일시 장애나 재시작, 다중 인스턴스 환경에서도 유실/중복 없이 알림이 처리되도록 만드는 데 초점을 두었습니다.

---

## 기술 스택

| 영역 | 선택 |
|---|---|
| 언어 | Kotlin 2.2.21 (JDK 21 toolchain) |
| 프레임워크 | Spring Boot 4.0.6 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`) |
| DB | PostgreSQL 16 (Docker) |
| 빌드 | Gradle (Groovy DSL), 멀티 모듈 |
| 직렬화 | Jackson Module Kotlin |
| 테스트 | JUnit 5, Spring Boot Test |

모듈 구조는 [`AGENTS.md`](./AGENTS.md)의 Clean Architecture + Monorepo 가이드를 따랐고, use case 단위 모듈 분리는 패키지 구분으로 대체했습니다. `bootstrap/notification/`을 진입점으로 `notification-api`(요청 접수), `notification-worker`(비동기 발송), `notification-batch`(스턱 복구·DLQ) 3개 부트스트랩 애플리케이션으로 나눴습니다.

use case별 모듈을 `port` / `port-in-impl` / `port-out-impl` 3분할 × 6 use case = 18 모듈로 만들면 도메인 규모 대비 빌드/IDE 비용이 너무 커서, 단일 `:usecase:notification` 모듈로 합치고 use case는 패키지(`usecase.sendnotification`, `usecase.dispatchnotification` 등)로 구분했습니다. 외부 기술이 use case 안으로 못 들어오는 의존 규칙은 모듈의 `dependencies`가 컴파일 단계에서 강제합니다.

---

## 실행 방법

### 사전 요구

- JDK 21 (Gradle 9 toolchain으로 자동 다운로드 가능)
- Docker Desktop (PostgreSQL 컨테이너, 통합 테스트 TestContainers)

### 단계

```bash
# 1) PostgreSQL 16 기동 (alarm/alarm/alarm_sender)
docker compose up -d postgres

# 2) API 서버 (port 8080) — Flyway가 V1 마이그레이션 자동 적용
./gradlew :bootstrap:notification:notification-api:bootRun

# 3) (별 터미널) 비동기 발송 워커 — 1초 주기 폴링
./gradlew :bootstrap:notification:notification-worker:bootRun

# 4) (별 터미널) 스턱 복구 배치 — 5초 주기 lease 만료 reclaim
./gradlew :bootstrap:notification:notification-batch:bootRun
```

테스트 실행:

```bash
./gradlew test          # 단위 + TestContainers 통합 테스트 전체
./gradlew compileKotlin # 타입/의존성 검증만 빠르게
```

---

## API 목록 및 예시

전체 계약은 [`docs/API_SPEC.yaml`](./docs/API_SPEC.yaml)에 있습니다. 주요 엔드포인트는 다음과 같습니다.

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/notifications` | 알림 발송 요청 / 예약 발송 (`Idempotency-Key` 헤더 권장) |
| GET | `/api/v1/notifications/{id}` | 단건 조회 (본인만 가능, 외엔 403) |
| GET | `/api/v1/notifications?unread_only=&limit=&offset=` | 본인 알림 목록 (최신순) |
| POST | `/api/v1/notifications/{id}/read` | 읽음 처리 (멀티 디바이스 멱등) |
| POST | `/api/v1/notifications/{id}/retry` | DEAD_LETTER 알림 수동 재시도 (`X-User-Role: OPERATOR` 필요) |
| GET/PUT | `/api/v1/notification-templates/{type}/{channel}` | 타입·채널별 알림 템플릿 조회/수정 |

### 발송 요청 예시

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: user-123' \
  -H 'Idempotency-Key: enroll-2026-05-03-001' \
  -d '{
    "recipient_id": "user-123",
    "type": "ENROLL_COMPLETED",
    "channel": "EMAIL",
    "payload": { "recipient_name": "홍길동", "course_name": "Spring Boot 입문" },
    "ref_type": "ENROLLMENT",
    "ref_id": "1001",
    "scheduled_at": "2026-05-03T12:00:00Z"
  }'
# → 201 Created (신규)
# → 200 OK (같은 Idempotency-Key 재요청 시 기존 알림 그대로)
```

응답 예시:

```json
{
  "id": 42,
  "recipient_id": "user-123",
  "type": "ENROLL_COMPLETED",
  "channel": "EMAIL",
  "payload": { "recipient_name": "홍길동", "course_name": "Spring Boot 입문" },
  "ref_type": "ENROLLMENT",
  "ref_id": "1001",
  "status": "PENDING",
  "read_at": null,
  "created_at": "2026-05-03T10:15:30Z",
  "scheduled_at": "2026-05-03T12:00:00Z",
  "sent_at": null
}
```

### 목록 조회

```bash
curl 'http://localhost:8080/api/v1/notifications?unread_only=true&limit=20' \
  -H 'X-User-Id: user-123'
```

### 읽음 처리

```bash
curl -X POST http://localhost:8080/api/v1/notifications/42/read \
  -H 'X-User-Id: user-123'
# → { "id": 42, "read_at": "2026-05-03T11:00:00Z", "newly_read": true }
# 두 번째 호출 → newly_read=false (no-op)
```

### 템플릿 수정

```bash
curl -X PUT http://localhost:8080/api/v1/notification-templates/ENROLL_COMPLETED/EMAIL \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: ops-1' \
  -H 'X-User-Role: OPERATOR' \
  -d '{
    "subject_template": "수강 신청 완료: {{course_name}}",
    "body_template": "{{recipient_name}}님, {{course_name}} 수강 신청이 완료되었습니다."
  }'
```

---

## 데이터 모델

### 테이블 구성

`notification` — 알림 본체.
- 컬럼: `id`, `recipient_id`, `type`, `channel`(EMAIL/IN_APP), `payload`(TEXT, JSON 직렬화 문자열), `idempotency_key`, `ref_type`, `ref_id`, `status`, `read_at`, `created_at`, `scheduled_at`, `sent_at`
- partial UNIQUE `idempotency_key WHERE idempotency_key IS NOT NULL`. 클라이언트 재시도 방어용입니다. NULL을 허용하면 다중 NULL이 가능하기 때문에 partial로 걸었습니다.
- partial UNIQUE `(recipient_id, type, ref_type, ref_id) WHERE ref_type IS NOT NULL AND ref_id IS NOT NULL`. 자연 키 중복 방어용이며, ref가 모두 있을 때만 강제합니다.
- INDEX `(recipient_id, created_at DESC)`. 사용자 알림 목록 조회용입니다.
- partial INDEX `(recipient_id, created_at DESC) WHERE read_at IS NULL`. 안 읽은 알림 조회 효율을 위한 인덱스입니다.

`notification_outbox` — 비동기 처리 큐.
- 컬럼: `id`, `notification_id`(FK, UNIQUE), `status`(PENDING/IN_PROGRESS/DONE/DEAD), `available_at`(다음 시도 시각), `attempt_count`, `lease_owner`, `lease_expires_at`, `last_error`(TEXT), `created_at`, `updated_at`
- partial INDEX `available_at WHERE status='PENDING'`. SKIP LOCKED 폴링 효율을 위한 인덱스입니다.
- partial INDEX `lease_expires_at WHERE status='IN_PROGRESS'`. lease 만료 reclaim 효율을 위한 인덱스입니다.

`notification_history` — 상태 전이 이력 (append-only, 감사·디버깅용).
- 컬럼: `id`, `notification_id`(FK), `from_status`, `to_status`, `reason`(CREATED/CLAIMED/SENT/TRANSIENT_FAILURE/EXHAUSTED/RECLAIMED/MANUAL_RETRY/READ), `detail`, `occurred_at`

`notification_template` — 타입·채널별 템플릿.
- 컬럼: `id`, `type`, `channel`, `subject_template`, `body_template`, `updated_at`
- UNIQUE `(type, channel)`. 같은 (type, channel)에 두 개 이상의 템플릿이 매칭되지 않도록 강제합니다.

### 상태 전이

```
[notification_outbox.status]
PENDING ──worker claim──▶ IN_PROGRESS ──ok──▶ DONE
   ▲                          │
   │ retry(available_at 갱신)  │ transient fail
   │                          ▼
   └──────────  attempt_count++ ───────────┐
                                            │ exceed max(=5)
                                            ▼
                                          DEAD ──manual retry──▶ PENDING (attempt_count=0)
   ▲
   └── batch reclaim (IN_PROGRESS AND lease_expires_at < now)

[notification.status]
PENDING → SENT 가 정상 흐름.
DEAD 격리 시 notification 은 DEAD_LETTER 로 전이. 수동 재시도 시 다시 PENDING.
```

---

> 상세 문서: 비동기 흐름·재시도·트랜잭션 경계는 [`docs/ASYNC_AND_RETRY.md`](./docs/ASYNC_AND_RETRY.md), 명세 해석과 개선 제안은 [`docs/REQUIREMENTS_INTERPRETATION.md`](./docs/REQUIREMENTS_INTERPRETATION.md), API 계약은 [`docs/API_SPEC.yaml`](./docs/API_SPEC.yaml)에 정리해두었습니다.

---

## 요구사항 해석 및 가정

명세에서 모호한 부분은 다음과 같이 해석했습니다. 자세한 근거는 [`docs/REQUIREMENTS_INTERPRETATION.md`](./docs/REQUIREMENTS_INTERPRETATION.md)에 정리해두었습니다.

- "동일 이벤트"의 정의 — `Idempotency-Key`(클라이언트 재시도 방어)와 `(recipient, type, ref_type, ref_id)` 자연 키(서버 측 중복 진입 방어) 두 축으로 보았습니다. 둘 다 DB UNIQUE 제약으로 막습니다.
- "비동기"의 범위 — API 트랜잭션은 알림과 outbox INSERT까지만 처리하고 바로 ACK합니다. 실제 발송은 `notification-worker`가 폴링해서 처리합니다.
- "실제 메시지 브로커 없이 운영 전환 가능" — `OutboxPublisher` 인터페이스를 두고 현재는 DB 폴링 어댑터를 사용합니다. Kafka/SQS 어댑터로 교체해도 도메인·유즈케이스 코드는 그대로 유지됩니다.
- "다중 인스턴스 중복 방지" — Postgres `SELECT ... FOR UPDATE SKIP LOCKED` + `lease_owner` / `lease_expires_at` short-lease 조합으로 처리합니다.
- "처리 중 상태가 일정 시간 이상 지속" — `IN_PROGRESS`이면서 `lease_expires_at < now()`인 row를 배치가 `PENDING`으로 되돌립니다.
- "서버 재시작 후 유실 없음" — 모든 중간 상태가 DB에 영속화되어 있어 워커가 재기동되면 그대로 이어 처리합니다.
- "예외를 단순히 무시하지 않음" — 실패는 항상 `notification_history`에 기록하고 `attempt_count` / `last_error`를 갱신합니다. 한도 초과 시 `DEAD_LETTER`로 격리합니다.
- "발송 예약" — `scheduled_at` 요청 필드를 outbox `available_at`으로 저장하고, 워커는 그 시각이 지난 row만 claim합니다.
- "템플릿 관리" — `notification_template`을 타입·채널별로 관리하고, 발송 시 payload를 `{{key}}` placeholder에 치환합니다.
- 인증/인가 — `X-User-Id` 헤더 기반 단순 식별로 처리했습니다 (과제 허용 범위 내).

---

## 설계 결정과 이유

1. Outbox 패턴 채택. 비즈니스 데이터 변경과 outbox INSERT가 한 트랜잭션에 묶여 원자적으로 처리됩니다. 브로커 없음, 운영 전환 가능, 재시작 무손실 세 요구사항을 한 번에 풀 수 있는 가장 단순한 구조라고 판단했습니다.
2. `SELECT ... FOR UPDATE SKIP LOCKED`. 다중 워커가 동시에 폴링해도 같은 row를 두 번 잡지 않고, 락 대기 없이 다음 row로 건너뛰어 throughput을 유지합니다. H2/MySQL 대신 Postgres를 선택한 이유이기도 합니다.
3. Lease 만료 기반 스턱 복구. 워커 죽음, GC pause, 네트워크 단절 어떤 이유든 `lease_expires_at` 만료만 보고 reclaim합니다. 워커의 정상 종료 신호에 의존하지 않습니다.
4. 재시도 정책. 지수 백오프 1m → 2m → 4m → 8m → 16m, 최대 5회로 잡았습니다. `available_at`을 미는 방식이라 별도 스케줄러 없이 같은 폴링 루프가 그대로 처리합니다.
5. 멱등성 2단 방어. HTTP 헤더 `Idempotency-Key`는 클라이언트 재시도 방어용, 자연 키는 서로 다른 호출자가 같은 이벤트로 들어올 때를 위한 방어용입니다. 둘 다 DB UNIQUE 제약이라 동시 race에서도 안전합니다.
6. 트랜잭션 범위. `AGENTS.md` 트랜잭션 규칙을 따랐습니다. API 측은 알림과 outbox INSERT까지만 한 트랜잭션으로 묶고, 외부 발송 호출은 트랜잭션 밖으로 분리했습니다. 부분 성공이 비즈니스 불변식을 깨지 않도록 경계를 잘랐습니다.
7. 읽음 처리 멀티 디바이스 동시성. `read_at IS NULL`일 때만 `now()`로 set, 이후 호출은 no-op으로 처리합니다. 이미 찍힌 시각을 덮어쓰지 않으므로 멀티 디바이스 동시 호출에서도 첫 시각이 보존됩니다.
8. 수동 재시도 시 `attempt_count` 정책. `DEAD_LETTER → PENDING` 전이 시 `attempt_count`를 0으로 리셋합니다. 운영자가 새로 시도를 거는 것으로 본다는 의미입니다. `notification_history`에 reason="MANUAL_RETRY"를 남겨 자동 재시도와 구분합니다.
9. 예약 발송. 별도 스케줄러를 추가하지 않고 outbox `available_at`을 예약 시각으로 둡니다. 기존 워커 폴링 조건(`available_at <= now`)이 예약 발송까지 그대로 커버합니다.

---

## 테스트 실행 방법

```bash
./gradlew test
```

자동화 검증을 마친 핵심 시나리오는 다음과 같습니다.

| 시나리오 | 검증 위치 |
|---|---|
| 같은 `Idempotency-Key` 두 번 요청 → 기존 알림 그대로 반환, outbox row 1개 | `SendNotificationServiceTest` / `NotificationControllerTest` |
| 자연 키 (recipient + type + ref) 중복 → 기존 알림 반환 | `SendNotificationServiceTest` |
| DB UNIQUE 제약 — 동시 race 마지막 보호선 | `NotificationRepositoryImplTest` |
| 다중 워커 동시 폴링 — 동일 row 두 번 잡지 않음 (SKIP LOCKED) | `NotificationOutboxRepositoryImplTest` (8 worker × 100 row 시나리오) |
| lease 만료 → reclaim으로 PENDING 복귀 | `NotificationOutboxRepositoryImplTest` / `ReclaimNotificationServiceTest` |
| 재시도 한도 초과 → outbox DEAD + notification DEAD_LETTER | `DispatchNotificationServiceTest` |
| 지수 백오프 (1m → 16m) | `NotificationOutboxTest` (도메인) |
| 멀티 디바이스 동시 read → read_at 한 번만 set | `ReadNotificationServiceTest` / `NotificationControllerTest` |
| 예약 발송 → outbox available_at이 scheduled_at으로 설정 | `SendNotificationServiceTest` / `NotificationControllerTest` |
| 템플릿 렌더링 및 운영자 수정 | `DispatchNotificationServiceTest` / `ManageNotificationTemplateServiceTest` / `NotificationControllerTest` |
| 본인 외 사용자 조회 시 403 | `NotificationControllerTest` |

레이어별 테스트 위치는 `AGENTS.md`의 매트릭스를 따랐습니다 (도메인은 단위, usecase는 test-stub 활용, 데이터/Web은 TestContainers PG).

---

## 미구현 / 제약사항

- 실제 SMTP 미연동. `EmailSender`는 로그 출력 Mock(`platform/email/email-impl`)으로 두고 인터페이스만 운영용으로 유지했습니다.
- IN_APP 푸시 채널은 DB 적재까지만 구현했습니다. 디바이스 연동(FCM 등)은 미구현입니다.
- 인증/인가는 `X-User-Id` 헤더 기반 단순 식별로 처리했습니다. JWT/OAuth는 미적용입니다.
- 분산 트레이싱·메트릭 미적용 (인터페이스조차 미노출).

---

## AI 활용 범위

- 사용 도구: Claude Code (Anthropic), Codex (OpenAI)
- 활용 방식
    - 설계 검토 및 트레이드오프 토론 (Outbox vs Spring `@Async` vs ApplicationEvent)
    - 명세와 설계에 따른 구현, 검증, 테스트
    - `docs/` 문서 정리
    - 테스트 엣지 케이스 분석
- 검증 방식: AI가 제안한 코드는 모두 직접 컴파일·테스트 실행으로 확인했습니다. 외부 라이브러리/API 호출은 공식 문서와 교차 확인했고, 설계 최종 결정은 직접 내렸습니다.
