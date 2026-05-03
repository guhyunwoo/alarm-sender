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
    ;

    companion object {
        /**
         * 헤더 문자열을 strict 하게 파싱한다. 인식 못 한 값은 silently USER 로 강등하지 않고
         * [IllegalArgumentException] 으로 던진다 (handler 에서 400 BAD_REQUEST 매핑).
         * 무지성 fallback 은 운영자가 오타를 냈을 때 OPERATOR 권한이 USER 로 떨어져
         * 디버깅이 어렵게 만드는 원인이라 명시 실패가 낫다.
         */
        fun parse(raw: String): RequesterRole {
            val normalized = raw.uppercase()
            return entries.firstOrNull { it.name == normalized }
                ?: throw IllegalArgumentException("알 수 없는 X-User-Role 값: '$raw' (가능한 값: ${entries.joinToString { it.name }})")
        }
    }
}
