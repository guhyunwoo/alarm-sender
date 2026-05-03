package ggee.alarmsender.library.exception

/**
 * 모든 비즈니스 예외의 공통 베이스. 도메인 / 유즈케이스 레이어에서 발생시키는 예외는
 * 반드시 이 클래스를 상속한다.
 *
 * 도메인 무관 공통 라이브러리이므로 library/exception 모듈에 둔다.
 * 새 도메인이 추가될 때 본 클래스만 의존하면 된다.
 *
 *  - `code`: 클라이언트 / 운영자가 식별할 수 있는 안정적인 오류 코드 (열거형 자체에 의미가 묶이지 않도록 String 으로 둔다)
 *  - `message`: 사람이 읽기 위한 설명. 식별자로 의존하지 말 것.
 *
 * HTTP 상태 매핑은 web 어댑터(GlobalExceptionHandler) 가 책임진다 —
 * HTTP 는 도메인의 관심사가 아니므로 이 클래스에 두지 않는다.
 *
 * 운영 컨벤션: 서브클래스는 `code` 와 `message` 를 명시적으로 넘기고,
 * 클래스 이름 자체로 의미가 충분하면 추가 데이터(notificationId 등) 만 생성자 파라미터로 받는다.
 */
abstract class BusinessBaseException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
