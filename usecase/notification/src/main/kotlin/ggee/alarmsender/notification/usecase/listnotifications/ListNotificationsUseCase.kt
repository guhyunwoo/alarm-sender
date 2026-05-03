package ggee.alarmsender.notification.usecase.listnotifications

import ggee.alarmsender.notification.domain.Notification
import ggee.alarmsender.notification.domain.exception.RecipientForbiddenException

interface ListNotificationsUseCase {
    /**
     * 특정 수신자(recipientId)의 알림 목록 조회.
     *
     * @throws RecipientForbiddenException
     *         requesterId 가 query.recipientId 와 다를 때 (본인 외 사용자 조회 차단).
     *         권한 검사는 use case 가 하고, 컨트롤러는 헤더만 넘긴다.
     */
    fun execute(query: ListNotificationsQuery): List<Notification>
}

data class ListNotificationsQuery(
    val recipientId: String,
    val requesterId: String,
    val unreadOnly: Boolean,
    val limit: Int,
    val offset: Int,
) {
    init {
        require(limit in 1..200) { "limit 은 1~200 사이여야 한다" }
        require(offset >= 0) { "offset 은 0 이상이어야 한다" }
    }
}
