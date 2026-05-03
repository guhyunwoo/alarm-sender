package ggee.alarmsender.notification.usecase.listnotifications

import ggee.alarmsender.notification.domain.Notification

interface ListNotificationsUseCase {
    fun execute(query: ListNotificationsQuery): List<Notification>
}

data class ListNotificationsQuery(
    val recipientId: String,
    val unreadOnly: Boolean,
    val limit: Int,
    val offset: Int,
) {
    init {
        require(limit in 1..200) { "limit 은 1~200 사이여야 한다" }
        require(offset >= 0) { "offset 은 0 이상이어야 한다" }
    }
}
