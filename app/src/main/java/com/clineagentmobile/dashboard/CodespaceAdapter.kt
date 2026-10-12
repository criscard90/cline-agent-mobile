package com.clineagentmobile.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.clineagentmobile.R
import com.clineagentmobile.model.Codespace

/**
 * RecyclerView adapter listing the user's Codespaces with quick actions.
 */
class CodespaceAdapter(
    private val onOpen: (Codespace) -> Unit,
    private val onStart: (Codespace) -> Unit,
    private val onStop: (Codespace) -> Unit,
    private val onDelete: (Codespace) -> Unit
) : RecyclerView.Adapter<CodespaceAdapter.CodespaceViewHolder>() {

    private val items = mutableListOf<Codespace>()

    fun submit(newItems: List<Codespace>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodespaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_codespace, parent, false)
        return CodespaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: CodespaceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CodespaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.cs_name)
        private val stateText: TextView = itemView.findViewById(R.id.cs_state)
        private val repoText: TextView = itemView.findViewById(R.id.cs_repo)
        private val metaText: TextView = itemView.findViewById(R.id.cs_meta)
        private val openButton: Button = itemView.findViewById(R.id.cs_open)
        private val startButton: Button = itemView.findViewById(R.id.cs_start)
        private val stopButton: Button = itemView.findViewById(R.id.cs_stop)
        private val deleteButton: Button = itemView.findViewById(R.id.cs_delete)

        fun bind(codespace: Codespace) {
            val context = itemView.context
            nameText.text = codespace.displayName?.takeIf { it.isNotBlank() } ?: codespace.name
            repoText.text = codespace.repository.fullName

            val meta = listOfNotNull(codespace.model, codespace.region).joinToString(" · ")
            if (meta.isBlank()) {
                metaText.visibility = View.GONE
            } else {
                metaText.visibility = View.VISIBLE
                metaText.text = meta
            }

            val (label, colorRes) = when (codespace.state) {
                Codespace.State.RUNNING ->
                    context.getString(R.string.dashboard_status_running) to R.color.status_running
                Codespace.State.STOPPED ->
                    context.getString(R.string.dashboard_status_stopped) to R.color.status_stopped
                Codespace.State.PAUSED ->
                    context.getString(R.string.dashboard_status_paused) to R.color.status_paused
                Codespace.State.CREATING ->
                    context.getString(R.string.dashboard_status_creating) to R.color.status_connecting
                Codespace.State.DELETING ->
                    context.getString(R.string.dashboard_status_deleting) to R.color.status_connecting
                Codespace.State.UNKNOWN ->
                    context.getString(R.string.dashboard_status_unknown) to R.color.status_connecting
            }
            stateText.text = label
            stateText.setTextColor(ContextCompat.getColor(context, colorRes))

            val hasUrl = !codespace.webUrl.isNullOrBlank() || !codespace.url.isNullOrBlank()
            openButton.visibility = if (hasUrl) View.VISIBLE else View.GONE
            startButton.visibility = if (codespace.isRunning) View.GONE else View.VISIBLE
            stopButton.visibility = if (codespace.isRunning) View.VISIBLE else View.GONE

            openButton.setOnClickListener { onOpen(codespace) }
            startButton.setOnClickListener { onStart(codespace) }
            stopButton.setOnClickListener { onStop(codespace) }
            deleteButton.setOnClickListener { onDelete(codespace) }
        }
    }
}
