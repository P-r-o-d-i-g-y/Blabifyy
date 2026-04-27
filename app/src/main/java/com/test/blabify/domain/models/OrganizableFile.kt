package com.test.blabify.domain.models

/**
 * Доменная модель файла для новой архитектуры организации вложений.
 * Используется контурами и координатором вместо вложенного типа старого FileAutoOrganizer.
 */
data class OrganizableFile(
    val id: String,
    val url: String,
    val name: String? = null,
    val pinned: Boolean? = null,
    val movedAt: Long? = null,
    val classificationBranch: String? = null,
    val classificationFolderId: String? = null,
    val classificationScore: Double? = null
)