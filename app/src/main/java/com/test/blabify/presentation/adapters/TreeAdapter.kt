package com.test.blabify.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.test.blabify.R
import com.test.blabify.domain.models.TreeItem
import com.test.blabify.domain.models.TreeItem.NodeType
import kotlin.math.max

class TreeAdapter(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onFolderToggle: (folderId: String, expanded: Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<TreeAdapter.VH>() {

    private val items = mutableListOf<TreeItem>()
    private val indentDp = 24
    private val density = context.resources.displayMetrics.density
    private val indentPx = (indentDp * density).toInt()

    init {
        setHasStableIds(true)
    }

    fun submitRoot(rootFolders: List<TreeItem>) {
        items.clear()
        items.addAll(rootFolders)
        notifyDataSetChanged()
    }

    /** Опционально: перезагрузить детей конкретной папки, если она раскрыта */
    fun refreshFolder(folderId: String) {
        val firstPos = items.indexOfFirst { it.type == NodeType.FOLDER && it.id == folderId }
        if (firstPos == -1) return
        if (!items[firstPos].isExpanded) return

        loadChildren(folderId,
            onSuccess = { children ->
                // список мог измениться — ищем позицию заново
                val posNow = items.indexOfFirst { it.type == NodeType.FOLDER && it.id == folderId }
                if (posNow == -1) return@loadChildren
                val parent = items[posNow]
                if (!parent.isExpanded) return@loadChildren

                val newChildren = children
                    .map { it.copy(level = parent.level + 1) }
                    .sortedWith(
                        compareBy<TreeItem> { it.type == NodeType.FILE }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )

                // удалить текущих потомков родителя на актуальной позиции
                val removed = removeDescendants(posNow)
                if (removed > 0) notifyItemRangeRemoved(posNow + 1, removed)

                // вставить новых
                val insertPos = (posNow + 1).coerceIn(0, items.size)
                if (newChildren.isNotEmpty()) {
                    items.addAll(insertPos, newChildren)
                    notifyItemRangeInserted(insertPos, newChildren.size)
                }

                // обновить иконку/состояние родителя
                notifyItemChanged(posNow)
            },
            onError = {
                // оставляем как есть
            }
        )
    }

    override fun getItemId(position: Int): Long {
        // стабильные ID: тип + id
        val prefix = if (items[position].type == NodeType.FOLDER) 1_000_000_000L else 2_000_000_000L
        return prefix + items[position].id.hashCode().toLong()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        val root: View = view.findViewById(R.id.root)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.tree_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.name.text = item.name

        // Отступ вправо по уровню --------- подозрительное место
        h.root.setPadding(
            (16 * density + item.level * indentPx).toInt(),
            h.root.paddingTop,
            h.root.paddingRight,
            h.root.paddingBottom
        )

        when (item.type) {
            NodeType.FOLDER -> {
                h.icon.setImageResource(if (item.isExpanded) R.drawable.folder_open2 else R.drawable.folder)
                //подозрительное место -------------
                val click = View.OnClickListener {
                    val currPos = h.adapterPosition
                    if (currPos == RecyclerView.NO_POSITION) return@OnClickListener

                    val curr = items.getOrNull(currPos) ?: return@OnClickListener
                    if (curr.type != NodeType.FOLDER) return@OnClickListener

                    if (curr.isExpanded) {
                        collapseAt(currPos)
                        onFolderToggle(curr.id, false)
                    } else {
                        expandAt(currPos) // внутри у нас коллапс соседей
                        onFolderToggle(curr.id, true)
                    }
                }
                h.icon.setOnClickListener(click)
                h.root.setOnClickListener(click)
            }
            NodeType.FILE -> {
                h.icon.setImageResource(R.drawable.ic_file)
                val click: (View) -> Unit = {
                    Toast.makeText(context, "Файл: ${item.name}", Toast.LENGTH_SHORT).show()
                }
                h.icon.setOnClickListener(click)
                h.root.setOnClickListener(click)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun collapseAt(position: Int) {
        if (position < 0 || position >= items.size) return
        val node = items[position]
        if (!node.isExpanded) return
        node.isExpanded = false
        notifyItemChanged(position)
        val removed = removeDescendants(position)
        if (removed > 0) notifyItemRangeRemoved(position + 1, removed)
    }

    private fun removeDescendants(parentPos: Int): Int {
        val parentLevel = items[parentPos].level
        var count = 0
        var i = parentPos + 1
        while (i < items.size && items[i].level > parentLevel) {
            count++
            i++
        }
        for (k in 0 until count) items.removeAt(parentPos + 1)
        return count
    }

    private fun collapseSiblings(position: Int) {
        val node = items[position]
        val parentId = node.parentId
        val level = node.level

        // Ищем все открытые папки-соседи и закрываем
        var i = position - 1
        while (i >= 0 && items[i].level >= level) {
            val it = items[i]
            if (it.level == level && it.type == NodeType.FOLDER && it.parentId == parentId && it.isExpanded) {
                collapseAt(i)
            }
            // если поднялись выше уровня — дальше назад смысла нет
            if (it.level < level) break
            i--
        }
        i = position + 1
        while (i < items.size && items[i].level >= level) {
            val it = items[i]
            if (it.level == level && it.type == NodeType.FOLDER && it.parentId == parentId && it.isExpanded) {
                collapseAt(i)
                // после collapseAt текущий диапазон сдвинется — позиция соседей сохранится,
                // но i не увеличиваем, чтобы не пропустить следующий элемент на новой позиции
            } else {
                i++
            }
        }
    }

    private fun expandAt(position: Int) {
        if (position < 0 || position >= items.size) return
        val nodeId = items[position].id

        // 1) Закрываем соседей — список может измениться
        collapseSiblings(position)

        // 2) Находим актуальную позицию текущего узла
        var currPos = items.indexOfFirst { it.type == NodeType.FOLDER && it.id == nodeId }
        if (currPos == -1) return
        val node = items[currPos]
        if (node.isExpanded) return

        // 3) Помечаем как раскрытый и обновляем иконку
        node.isExpanded = true
        notifyItemChanged(currPos)

        // 4) Грузим детей
        loadChildren(node.id,
            onSuccess = { children ->
                // Позиция могла снова измениться (анимации, другие операции) — ищем её ещё раз
                val posNow = items.indexOfFirst { it.type == NodeType.FOLDER && it.id == nodeId }
                if (posNow == -1) return@loadChildren
                if (!items[posNow].isExpanded) return@loadChildren  // уже свернули — ничего не вставляем

                val toInsert = children
                    .map { it.copy(level = items[posNow].level + 1) }
                    .sortedWith(
                        compareBy<TreeItem> { it.type == NodeType.FILE }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )

                val insertPos = posNow + 1
                // Страховка от выхода за границы
                val safeInsertPos = insertPos.coerceIn(0, items.size)
                items.addAll(safeInsertPos, toInsert)
                if (toInsert.isNotEmpty()) notifyItemRangeInserted(safeInsertPos, toInsert.size)
            },
            onError = {
                // Откатываем флаг, если не удалось загрузить
                val posNow = items.indexOfFirst { it.type == NodeType.FOLDER && it.id == nodeId }
                if (posNow != -1) {
                    items[posNow].isExpanded = false
                    notifyItemChanged(posNow)
                }
                Toast.makeText(context, "Не удалось загрузить содержимое", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadChildren(
        folderId: String,
        onSuccess: (List<TreeItem>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val folderDoc = db.collection("folders").document(folderId)

        // 1) Подпапки: ищем документы /folders где parentId == folderId
        db.collection("folders")
            .whereEqualTo("parentId", folderId)
            .get()
            .addOnSuccessListener { subSnap ->
                val folderChildren = subSnap.documents.map {
                    TreeItem(
                        id = it.id,
                        name = it.getString("name") ?: "(без имени)",
                        parentId = folderId,
                        level = 0,
                        type = NodeType.FOLDER,
                        isExpanded = false
                    )
                }

                // 2) Файлы текущей папки
                folderDoc.collection("files")
                    .get()
                    .addOnSuccessListener { filesSnap ->
                        val fileChildren = filesSnap.documents.map {
                            TreeItem(
                                id = it.id,
                                name = it.getString("name") ?: "(файл)",
                                parentId = folderId,
                                level = 0,
                                type = NodeType.FILE
                            )
                        }

                        onSuccess(
                            (folderChildren + fileChildren).sortedWith(
                                compareBy<TreeItem> { it.type == NodeType.FILE }
                                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                            )
                        )
                    }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }
}