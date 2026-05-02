# 저장소 가이드라인

## 프로젝트 구조 및 모듈 구성
이 프로젝트는 **Clean Architecture + Monorepo** 원칙을 기반으로 한 Gradle 멀티 모듈 Kotlin/Spring Boot 백엔드입니다.

### 최상위 디렉터리 구조

```
.
├── _endpoint-test/          # HTTP, gRPC, GraphQL 등 API 호출 테스트 모음
├── bootstrap/               # 실행 가능한 Spring Boot 애플리케이션 모음
│   └── notification/
│       ├── notification-api/
│       ├── notification-batch/
│       └── notification-worker/
├── core/                    # 코어 도메인 모듈
│   └── <domain>/
│       ├── domain/          # 도메인 모델 및 비즈니스 규칙 (외부 기술 의존 없음)
│       ├── data/            # Repository 구현체
│       ├── shared/          # 도메인 내 공통 컴포넌트
│       ├── test-fixture/    # 테스트용 도메인 객체 팩토리
│       └── test-stub/       # 테스트용 Fake/in-memory 구현체
├── infrastructure/          # 애플리케이션 실행에 필요한 인프라 기반 모듈
│   ├── grpc/
│   ├── kafka/
│   ├── spring-web/
│   └── redis/
├── library/                 # 도메인 비즈니스와 무관한 공통 라이브러리 모듈
│   └── async/
│       ├── interface/
│       ├── impl/
│       └── test-stub/
├── platform/                # 도메인 비즈니스와 관련된 외부 서비스 연동 모듈
│   └── push/
│       ├── interface/
│       ├── impl/
│       └── test-stub/
├── usecase/                 # 비즈니스 로직 오케스트레이션 — 유즈케이스 단위 모듈
│   └── send-notification/
│       ├── port/            # Input/Output Port 인터페이스
│       ├── port-in-impl/    # Input Port 구현체 (유즈케이스 서비스)
│       ├── port-out-impl/   # Output Port 구현체
│       ├── test-fixture/
│       └── test-stub/
├── sql/
├── static/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### 모듈 역할 및 의존 규칙

핵심 원칙은 **The Dependency Rule** 하나다: **의존성은 항상 안쪽(비즈니스 규칙)으로만 향한다.**

```
bootstrap
  └── usecase (port-in-impl, port-out-impl)
        └── core:<domain>:domain   ← 순수 Kotlin, 외부 의존 없음
        └── usecase:port           ← 인터페이스만 존재
              ▲ impl
          core:<domain>:data       ← Repository 구현체
          platform:impl            ← 외부 서비스 어댑터
                ▲ impl
            platform:interface
            infrastructure         ← DB, Kafka, Redis, gRPC 설정
library:impl ──► library:interface
```

| 모듈 | 역할 | 참조 가능 대상 |
|---|---|---|
| `core:<domain>:domain` | Entity, Value Object, Domain Event, Repository 인터페이스 | 없음 |
| `core:<domain>:data` | Repository 구현체, DB 매핑 | `core:<domain>:domain` |
| `usecase:<name>:port` | Input/Output Port 인터페이스 | `core:<domain>:domain` |
| `usecase:<name>:port-in-impl` | 유즈케이스 서비스 구현체 | `usecase:port`, `core:<domain>:domain` |
| `usecase:<name>:port-out-impl` | Output Port 연결 | `core:<domain>:data`, `platform:interface` |
| `platform:interface` | 외부 서비스 계약 인터페이스 | `library:interface` |
| `platform:impl` | 외부 서비스 어댑터 (FCM, 인증 등) | `platform:interface`, `infrastructure` |
| `library` | Async, Logging, Retry 등 도메인 무관 유틸리티 | 없음 (또는 `infrastructure`만) |
| `infrastructure` | DB/Kafka/Redis/gRPC 기술 설정 | 외부 라이브러리만 |
| `bootstrap` | DI 조립, 애플리케이션 진입점 | 위 모든 모듈 |

> `bootstrap`은 모든 레이어를 참조할 수 있는 **유일한** 모듈이다. 비즈니스 로직을 포함해서는 안 된다.

### 새 도메인 추가 순서

의존 규칙이 깨지지 않도록 아래 순서를 지킨다:

1. `core/<domain>/domain/` — Entity, Value Object, Repository 인터페이스 정의
2. `core/<domain>/data/` — Repository 구현체, DB 매핑 추가
3. `core/<domain>/test-fixture/` — 도메인 객체 팩토리 작성
4. `core/<domain>/test-stub/` — Fake Repository 구현체 작성
5. `usecase/<use-case>/port/` — Input/Output Port 인터페이스 정의
6. `usecase/<use-case>/port-in-impl/` — 유즈케이스 서비스 구현
7. `usecase/<use-case>/port-out-impl/` — Output Port를 `core:data` / `platform`에 연결
8. `bootstrap/<app>/` — DI 조립, 설정 파일 추가
9. `docs/API_SPEC.yaml` — API 계약 업데이트

- 소스 파일은 `src/main/kotlin`, 테스트 파일은 `src/test/kotlin` 아래에 위치한다.

---

## 빌드, 테스트, 개발 명령어
- `./gradlew compileKotlin --console=plain`: 전체 모듈 빠른 컴파일 확인.
- `./gradlew test --console=plain`: JUnit 5 테스트 스위트 실행.
- `./gradlew clean build --console=plain`: 패키징 포함 전체 빌드.

한 영역만 변경할 경우 모듈 범위 태스크를 사용한다. 예:
```
./gradlew :usecase:send-notification:port-in-impl:compileKotlin
./gradlew :core:notification:domain:compileKotlin
./gradlew :bootstrap:notification:notification-api:compileKotlin
```

## 코딩 스타일 및 네이밍 컨벤션
- `.editorconfig`를 준수한다: UTF-8, LF, 파일 끝 개행.
- Java 파일은 탭 너비 4를 사용하며, Kotlin 포맷은 기존 코드베이스와 일관성을 유지한다.
- 패키지명은 소문자를 사용한다. 예:
    - `com.example.notification.domain`
    - `com.example.notification.usecase.sendnotification`
    - `com.example.notification.bootstrap.api`
- 클래스는 PascalCase를 사용한다.
- 모듈 경계를 명확히 유지한다:
    - 도메인 모델/비즈니스 규칙 → `core/<domain>/domain/`
    - 유즈케이스/비즈니스 로직 → `usecase/<use-case>/port-in-impl/`
    - HTTP 어댑터/컨트롤러 → `bootstrap/<app>/`
    - 외부 서비스 연동 → `platform/<service>/impl/`
    - DB/메시징 기술 설정 → `infrastructure/`

## 테스트 가이드라인
- 테스트는 Gradle을 통해 JUnit 5를 사용한다.
- 테스트는 검증 대상 모듈 옆 `src/test/kotlin` 아래에 위치시킨다.
- 이름 끝에 `Test` 또는 `Tests`를 붙인다. 예: `SendNotificationUseCaseTest`, `NotificationRepositoryTest`.
- 공개 흐름을 변경할 때는 컨트롤러 연결, 경계 규칙, 서비스 동작에 대한 집중적인 모듈 레벨 테스트를 추가한다.

### 레이어별 테스트 위치

| 테스트 대상 | 위치 | 방식 |
|---|---|---|
| 도메인 로직 | `core/<domain>/domain/src/test/kotlin` | 순수 단위 테스트 |
| 유즈케이스 | `usecase/<use-case>/port-in-impl/src/test/kotlin` | test-stub 활용 |
| Web 레이어 | `bootstrap/<app>/src/test/kotlin` | `WebTestClient` |
| DB 레이어 | `core/<domain>/data/src/test/kotlin` | TestContainers |

### TDD 규칙

- 기능 추가나 버그 수정은 가능하면 TDD 순서로 진행한다: 실패하는 테스트 작성 → 최소 구현 → 리팩터링.
- 구현 코드보다 테스트를 먼저 작성해 요구사항과 기대 동작을 고정한다.
- 테스트 없이 동작을 추측하며 구현을 넓히지 않는다. 먼저 실패하는 테스트로 범위를 잠근다.
- 버그 수정은 재현 테스트를 먼저 추가하고, 그 테스트가 실패하는 것을 확인한 뒤 수정한다.
- 리팩터링은 기존 테스트가 보호하고 있는 상태에서만 진행한다. 리팩터링 중 동작 변경이 필요하면 테스트 기대값부터 갱신한다.

### 테스트/도메인 설계 원칙

- 테스트는 내부 구현을 드러내지 않는다.
    - private/helper 메서드, 내부 호출 순서, 위임 여부 자체를 검증하지 않는다.
    - 사용자가 관찰 가능한 입력/출력, 상태 변화, 발행 이벤트, 외부 계약을 기준으로 검증한다.

- 테스트에서는 바깥만 모킹하고 안쪽은 진짜를 사용한다.
    - DB, 외부 API, 메시징, 시간, 랜덤, 파일 I/O 같은 프로세스 밖 경계만 test double로 대체한다.
    - `usecase`, `core:domain` 내부 협력 객체는 가능하면 실제 구현을 조합해서 테스트한다.
    - Mockito/MockK 남용보다 `core/<domain>/test-stub/`, `usecase/<use-case>/test-stub/`의 fake/in-memory 구현을 우선한다.

- 비즈니스 로직은 서비스보다 데이터 자체에 우선 배치한다.
    - 상태 전이, 불변식, 계산 규칙, 유효성 규칙은 `core/<domain>/domain/`의 Entity/Value Object가 직접 표현하도록 설계한다.
    - `usecase`는 오케스트레이션과 경계 연결에 집중하고, 핵심 업무 규칙을 과도하게 소유하지 않는다.
    - getter 확인 위주의 빈약한 도메인보다, 행위를 가진 객체와 그 행위를 검증하는 테스트를 우선한다.

## 컴파일 검증 (필수)
코드 변경 후에는 **항상** 아래 명령으로 컴파일 확인을 한 뒤 작업을 완료로 간주한다:
```
./gradlew compileKotlin compileTestKotlin --console=plain
```
단일 모듈 범위의 변경이라면 모듈 범위 명령을 사용한다. 예:
```
./gradlew :usecase:send-notification:port-in-impl:compileKotlin :usecase:send-notification:port-in-impl:compileTestKotlin --console=plain
./gradlew :core:notification:domain:compileKotlin :core:notification:domain:compileTestKotlin --console=plain
```
이 단계를 생략하지 않는다. 임포트, 생성자 변경, 클래스 삭제 등 관련 파일의 컴파일 에러는 작업 완료 전에 반드시 잡아야 한다.

풀 리퀘스트 작성 시:
- 변경된 모듈과 영향받는 흐름을 기술한다.
- 관련 이슈 또는 티켓을 링크한다.
- 테스트/컴파일 결과를 포함한다.
- API 동작이 변경된 경우 요청/응답 예시를 추가한다.

## API 명세

`docs/API_SPEC.yaml`은 프론트엔드와 소통하는 **유일한 API 계약서**다.

아래 변경이 발생하면 **반드시** 해당 파일을 함께 수정해야 한다:
- 컨트롤러에 엔드포인트 추가 / 삭제 / 경로 변경
- 요청(Request) 또는 응답(Response) 필드 추가 / 삭제 / 타입 변경
- 헤더 요구사항 변경
- 인증 방식 변경 (PERMIT_ALL / OPTIONAL_AUTH / AUTHENTICATED / ADMIN_ONLY)
- HTTP 상태 코드 변경

## 보안 및 설정 팁
- `.env`의 시크릿은 커밋하지 않는다.

### 트랜잭션 규칙 (엄격)

- 트랜잭션은 기본값이 아니다. 무분별한 트랜잭션 사용을 금지한다.
    - 단일 insert/update/delete 1회, 단순 CRUD, read-only 흐름에는 트랜잭션을 사용하지 않는다.
    - 트랜잭션은 리소스/락/컨텍스트 비용이 있으므로 "필요한 곳에만 최소 범위로" 적용한다.

- 원자성(atomicity)이 반드시 필요한 경우에만 트랜잭션을 사용한다.
    - "A가 실패하면 B도 반드시 롤백"되어야 하는 원자성(all-or-nothing)이 요구될 때만 트랜잭션을 사용한다.
    - 예시 (트랜잭션 필요):
        - 2개 이상 DB write가 하나의 업무 단위로 묶여야 함
          (예: 알림 생성 + 발송 이력 기록)
        - 비즈니스 불변식(invariant)을 지키기 위한 다중 쓰기
          (예: 알림 상태 변경 + 이력 적재)

- 예시 (트랜잭션 불필요):
    - 단일 저장/업데이트 1회
    - 조회-only
    - 캐시 갱신, 로그 적재 같은 부수효과
      → 실패해도 본 DB 작업을 롤백시키면 안 되는 작업은 트랜잭션 밖으로 분리
      (이벤트/비동기/아웃박스 패턴 등 고려)

- 롤백 정책:
    - 트랜잭션 파이프라인에서 에러가 발생하면 error 신호가 전파되어 롤백되도록 작성한다.
    - 트랜잭션 내부에서 `onErrorResume`로 에러를 삼키지 않는다(명시적 보상 트랜잭션 제외).

- 트랜잭션 추가 전 체크리스트:
    1) 2개 이상 DB write가 있으며 all-or-nothing이 반드시 필요하다.
    2) 부분 성공이 비즈니스 불변식을 깨거나 잘못된 상태를 외부에 노출한다.
    3) 트랜잭션 경계에 원격 호출/메시징/블로킹 I/O가 포함되지 않는다.

## 새 API 엔드포인트 추가 체크리스트

1) 테스트 추가:
    - Web layer: `WebTestClient` (`bootstrap/<app>/src/test/kotlin/.../controller/`)
    - 유즈케이스 단위 테스트: `usecase/<use-case>/port-in-impl/src/test/kotlin/`
2) 실행:
    - `./gradlew test` 및 `./gradlew check`
3) API 문서 반영:
    - API를 추가하거나 수정한 경우 `docs/API_SPEC.yaml`을 반드시 함께 수정한다.

## 안전한 변경 정책 (agent guidance)

- 무조건 기존 코드베이스의 구조, 패턴, 아키텍처를 따른다.
- 환경변수의 기본값은 코드, 설정, 문서에 임의로 명시하지 않는다.
- 새 프레임워크를 도입하기보다 기존 패턴을 확장하는 방식을 우선한다.
- 제안보다 저장소 규칙이 우선한다.
- 위 CLI 명령으로 build/tests 통과를 항상 확인한다.

## 커밋 규칙

- 사용자가 커밋을 요청하면 반드시 `COMMIT.md`를 먼저 확인하고 그 규칙을 따른다.
- 커밋은 코드 변경 역할과 책임에 따라 작고 명확한 단위로 나눈다.
- 서로 다른 성격의 변경(예: 리팩터링, 기능 추가, 버그 수정, 테스트, 문서 수정)은 분리해서 커밋한다.
- 커밋 메시지는 반드시 한국어로 작성한다.
