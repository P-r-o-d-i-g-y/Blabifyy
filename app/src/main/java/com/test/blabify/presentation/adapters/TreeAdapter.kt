package com.test.blabify.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
        val pos = items.indexOfFirst { it.type == NodeType.FOLDER && it.id == folderId }
        if (pos == -1) return
        val node = items[pos]
        if (!node.isExpanded) return
        // свернуть без смены иконки
        val removed = removeDescendants(pos)
        if (removed > 0) notifyItemRangeRemoved(pos + 1, removed)
        // раскрыть заново
        expandAt(pos)
    }

    override fun getItemId(position: Int): Long {
        // стабильные ID: тип + id
        val prefix = if (items[position].type == NodeType.FOLDER) 1_000_000_000L else 2_000_000_000L
        return prefix + items[position].id.hashCode().toLong()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageButton = view.findViewById(R.id.icon)
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

        // Отступ вправо по уровню
        h.root.setPadding(
            16 * density.toInt() + item.level * indentPx,
            h.root.paddingTop,
            h.root.paddingRight,
            h.root.paddingBottom
        )

        when (item.type) {
            NodeType.FOLDER -> {
                h.icon.setImageResource(if (item.isExpanded) R.drawable.folder_open2 else R.drawable.folder)
                val click: (View) -> Unit = {
                    if (item.isExpanded) {
                        collapseAt(position)
                        onFolderToggle(item.id, false)
                    } else {
                        expandAt(position)
                        onFolderToggle(item.id, true)
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

    private fun expandAt(position: Int) {
        if (position < 0 || position >= items.size) return
        val node = items[position]
        if (node.type != NodeType.FOLDER || node.isExpanded) return

        node.isExpanded = true
        notifyItemChanged(position)

        // грузим детей из Firestore
        loadChildren(node.id,
            onSuccess = { children ->
                // выставляем уровень и сортируем: папки сперва, затем файлы, потом по имени
                val toInsert = children
                    .map { it.copy(level = node.level + 1) }
                    .sortedWith(
                        compareBy<TreeItem> { it.type == NodeType.FILE }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )

                val insertPos = position + 1
                items.addAll(insertPos, toInsert)
                if (toInsert.isNotEmpty()) notifyItemRangeInserted(insertPos, toInsert.size)
            },
            onError = {
                // если не удалось загрузить — откатим флаг
                node.isExpanded = false
                notifyItemChanged(position)
                Toast.makeText(context, "Не удалось загрузить содержимое", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadChildren(
        folderId: String,
        onSuccess: (List<TreeItem>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        // Сначала папки
        db.collection("folders")
            .document(folderId)
            .collection("childFolders")
            .get()
            .addOnSuccessListener { cfSnap ->
                val folderChildren = cfSnap.documents.map {
                    TreeItem(
                        id = it.id,
                        name = it.getString("name") ?: "(без имени)",
                        parentId = folderId,
                        level = 0,
                        type = NodeType.FOLDER,
                        isExpanded = false
                    )
                }

                // Затем файлы
                db.collection("folders")
                    .document(folderId)
                    .collection("files")
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
                        onSuccess(folderChildren + fileChildren)
                    }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }
}