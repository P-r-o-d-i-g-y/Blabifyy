package com.test.blabify.domain.models

data class TreeItem(
    val id: String,
    val name: String,
    val parentId: String?,       // null для корня
    var level: Int,               // 0 для корней, +1 на каждый уровень
    val type: NodeType,
    var isExpanded: Boolean = false // только для папок имеет смысл
) {
    enum class NodeType { FOLDER, FILE }
}