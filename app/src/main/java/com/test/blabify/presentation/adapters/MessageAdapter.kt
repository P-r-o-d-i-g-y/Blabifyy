package com.test.blabify.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R
import com.test.blabify.domain.models.Message
import com.test.blabify.domain.models.AttachmentType
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.log10
import kotlin.math.pow

class MessageAdapter(
    private val context: Context,
    private val messages: List<Message>,
    private val onFileClick: (message: Message) -> Unit = {},
    private val onDetailsClick: (message: Message) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private enum class ViewType { TEXT, FILE }

    override fun getItemViewType(position: Int): Int {
        val m = messages[position]
        val isFile = !m.attachmentUrl.isNullOrBlank() && m.attachmentType != null
        return (if (isFile) ViewType.FILE else ViewType.TEXT).ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (ViewType.values()[viewType]) {
            ViewType.TEXT -> {
                val v = inflater.inflate(R.layout.chat_message_row, parent, false)
                TextVH(v)
            }
            ViewType.FILE -> {
                val v = inflater.inflate(R.layout.chat_file_message_row, parent, false)
                FileVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val model = messages[position]
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        when (holder) {
            is TextVH -> bindText(holder, model, currentUserId)
            is FileVH -> bindFile(holder, model, currentUserId)
        }
    }

    override fun getItemCount(): Int = messages.size

    // --- ТЕКСТ ---

    private fun bindText(holder: TextVH, m: Message, currentUserId: String?) {
        val isMine = m.senderId == currentUserId

        if (isMine) {
            holder.leftChatLayout.visibility = View.GONE
            holder.rightChatLayout.visibility = View.VISIBLE
            holder.rightChatTextview.text = m.textMessage.orEmpty()
        } else {
            holder.rightChatLayout.visibility = View.GONE
            holder.leftChatLayout.visibility = View.VISIBLE
            holder.leftChatTextview.text = m.textMessage.orEmpty()
        }
    }

    inner class TextVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val leftChatLayout: LinearLayout = itemView.findViewById(R.id.left_chat_layout)
        val rightChatLayout: LinearLayout = itemView.findViewById(R.id.right_chat_layout)
        val leftChatTextview: TextView = itemView.findViewById(R.id.left_chat_textview)
        val rightChatTextview: TextView = itemView.findViewById(R.id.right_chat_textview)
    }

    // --- ФАЙЛ ---

    private fun bindFile(holder: FileVH, m: Message, currentUserId: String?) {
        val isMine = m.senderId == currentUserId
        val fileName = (m.fileName?.takeIf { it.isNotBlank() }) ?: deriveFileName(m)
        val sizeText = m.fileSize?.let { formatBytes(it) } ?: ""

        if (isMine) {
            // показываем правый «мой» пузырь
            holder.leftChatLayout.visibility = View.GONE
            holder.rightChatLayout.visibility = View.VISIBLE

            holder.rightFileName.text = fileName
            holder.rightFileSize.text = sizeText

            holder.rightFileButton.setOnClickListener { onFileClick(m) }
            holder.rightDetailsButton.setOnClickListener { onDetailsClick(m) }
        } else {
            // показываем левый «чужой» пузырь
            holder.rightChatLayout.visibility = View.GONE
            holder.leftChatLayout.visibility = View.VISIBLE

            holder.leftFileName.text = fileName
            holder.leftFileSize.text = sizeText

            holder.leftFileButton.setOnClickListener { onFileClick(m) }
            holder.leftDetailsButton.setOnClickListener { onDetailsClick(m) }
        }
    }

    inner class FileVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Общие контейнеры
        val leftChatLayout: LinearLayout = itemView.findViewById(R.id.left_chat_layout)
        val rightChatLayout: LinearLayout = itemView.findViewById(R.id.right_chat_layout)

        // Левый (полученные)
        val leftFileButton: ImageButton = itemView.findViewById(R.id.left_file_button)
        val leftDetailsButton: ImageButton = itemView.findViewById(R.id.left_details_button)
        val leftFileName: TextView = itemView.findViewById(R.id.left_chat_file)
        val leftFileSize: TextView = itemView.findViewById(R.id.left_file_size)

        // Правый (мои)
        val rightFileButton: ImageButton = itemView.findViewById(R.id.right_file_button)
        val rightDetailsButton: ImageButton = itemView.findViewById(R.id.right_details_button)
        val rightFileName: TextView = itemView.findViewById(R.id.right_chat_file)
        val rightFileSize: TextView = itemView.findViewById(R.id.right_file_size)
    }

    // --- Утилиты ---

    private fun deriveFileName(m: Message): String {
        // Пытаемся вытащить имя из URL, иначе даём типовой плейсхолдер
        val url = m.attachmentUrl.orEmpty()
        val guessed = url.substringAfterLast('/', missingDelimiterValue = "")
            .substringBefore('?')
        if (guessed.isNotBlank()) return guessed

        return when (m.attachmentType) {
            AttachmentType.IMAGE -> "Image"
            AttachmentType.PDF -> "Document.pdf"
            AttachmentType.TABLE -> "Spreadsheet"
            AttachmentType.FILE, null -> "File"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.lastIndex + 1)
        val value = bytes / 1024.0.pow(exp.toDouble())
        val unit = units[exp - 1]
        // до одного знака после запятой, без лишних .0
        val rounded = if (value >= 100) "%.0f".format(value) else "%.1f".format(value)
        return "$rounded $unit"
    }
}