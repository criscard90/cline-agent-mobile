package com.clineagentmobile.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.clineagentmobile.R
import com.clineagentmobile.model.Message
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

/**
 * RecyclerView adapter rendering the chat conversation. User messages are
 * right-aligned on an accent bubble, assistant messages left-aligned on a
 * neutral bubble.
 */
class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val items = mutableListOf<Message>()
    private var markdownRenderer: Markwon? = null

    fun submit(newItems: List<Message>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: com.google.android.material.card.MaterialCardView =
            itemView.findViewById(R.id.msg_card)
        private val content: TextView = itemView.findViewById(R.id.msg_content)

        fun bind(message: Message) {
            val context = itemView.context
            val isUser = message.role == Message.Role.USER

            if (message.role == Message.Role.ASSISTANT) {
                val renderer = markdownRenderer ?: Markwon.builder(context)
                    .usePlugin(TablePlugin.create(context))
                    .build()
                    .also { markdownRenderer = it }
                renderer.setMarkdown(content, message.text)
            } else {
                content.text = message.text
            }

            val cardLp = card.layoutParams as FrameLayout.LayoutParams
            cardLp.gravity = if (isUser) Gravity.END else Gravity.START
            card.layoutParams = cardLp

            if (isUser) {
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                content.setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            } else {
                card.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.chat_message_assistant_secondary)
                )
                content.setTextColor(ContextCompat.getColor(context, R.color.on_background))
            }
        }
    }
}
