package com.test.blabify.domain.models

data class ChatRoom(
    val chatId: String = "",
    val title: String = "",
    val ownerId: String = "",
    val subtitle: String = "Вы: сообщений нет", // был subtitle в ChatItem
    val iconResId: Int = 0,                    // был iconResId в ChatItem
    var mark: String = "blue",                 // был mark в ChatItem
    val participantIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)