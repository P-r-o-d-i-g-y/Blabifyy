package com.test.blabify.domain.models


data class Folder(
    val id: String = "",
    val name: String = "",
    val parentId: String? = null,  // null = корневая папка
    val topicId: String? = null,   // если в будущем темы будут родителями
    val chatId: String? = null,    // для текущей реализации
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)