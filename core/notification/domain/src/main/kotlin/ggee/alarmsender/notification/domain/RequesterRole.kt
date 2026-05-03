package ggee.alarmsender.notification.domain

/**
 * 요청자 권한. 현재 X-User-Id 단순 인증 환경에서 X-User-Role 헤더로 전달된다.
 * 운영 환경에서는 JWT/OAuth2 의 role claim 으로 자연스럽게 매핑된다.
 *
 *  - USER     : 일반 사용자. 본인 알림 조회/읽음 처리만 가능.
 *  - OPERATOR : 운영자. DEAD_LETTER 수동 재시도 등 시스템 상태를 변경하는 작업 수행 가능.
 */
enum class RequesterRole {
    USER,
    OPERATOR,
}
