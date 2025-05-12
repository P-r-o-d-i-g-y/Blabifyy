package com.test.blabify.domain.models

data class Message(
    val userName: String = "",
    val senderId: String = "",
    val textMessage: String? = null,
    val messageTime: Long = System.currentTimeMillis(),

    val attachmentUrl: String? = null,
    val attachmentType: AttachmentType? = null,

    val fileName: String? = null,   // имя файла
    val fileSize: Long? = null      // размер в байта
)