package com.test.blabify.domain.models

data class FileMeta(
    val id: String = "",          // удобен при чтении
    val name: String = "",
    val size: Long = 0L,
    val url: String = "",
    val type: String = "",        // "IMAGE"/"PDF"/...
    val uploadedAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null,

    // поля под политику сортировки
    val pinned: Boolean = false,
    val confidence: Float? = null,
    val movedAt: Long? = null,


    // НОВОЕ: липкость к ветке
    val classificationBranch: String? = null,   // имя первой папки в path (ветка), например "puppy"
    val classificationFolderId: String? = null, // последний выбранный лист
    val classificationScore: Double? = null,    // последний score



    // опционально для изображений (можно добавить позже)
    val exifDate: String? = null,
    val exifCamera: String? = null,
    val exifLat: Double? = null,
    val exifLng: Double? = null,

    val chatId: String? = null,        //ускорение
    val rootFolderId: String? = null

)