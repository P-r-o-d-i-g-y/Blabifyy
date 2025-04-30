package com.test.blabify.domain.models

data class Message(
    val userName: String = "",
    val textMessage: String? = null,
    val messageTime: Long = System.currentTimeMillis(),

    val attachmentUrl: String? = null,
    val attachmentType: AttachmentType? = null
)