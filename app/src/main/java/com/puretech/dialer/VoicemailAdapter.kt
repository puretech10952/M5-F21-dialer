package com.puretech.dialer

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** One stored voicemail (from VoicemailContract) shown in the voicemail list. */
data class VoicemailItem(
    val id: Long,
    val number: String,
    val displayName: String?,
    val dateMillis: Long,
    val durationSec: Long,
    val isRead: Boolean,
    val hasContent: Boolean
)

/** Voicemail list: tap to play/pause, call-back and delete buttons per row. */
class VoicemailAdapter(
    private val onPlay: (VoicemailItem) -> Unit,
    private val onCall: (VoicemailItem) -> Unit,
    private val onDelete: (VoicemailItem) -> Unit
) : RecyclerView.Adapter<VoicemailAdapter.VH>() {

    private val items = ArrayList<VoicemailItem>()
    private var playingId: Long = -1L

    fun submit(list: List<VoicemailItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun setPlaying(id: Long) {
        val old = playingId
        playingId = id
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_voicemail, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.vmTitle)
        private val subtitle: TextView = view.findViewById(R.id.vmSubtitle)
        private val play: ImageView = view.findViewById(R.id.vmPlay)
        private val callBtn: ImageView = view.findViewById(R.id.vmCall)
        private val delete: ImageView = view.findViewById(R.id.vmDelete)
        private val unread: View = view.findViewById(R.id.vmUnread)

        fun bind(item: VoicemailItem) {
            val ctx = title.context
            title.text = item.displayName
                ?: item.number.ifBlank { ctx.getString(R.string.unknown_caller) }
            val when_ = DateUtils.getRelativeTimeSpanString(
                item.dateMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            val dur = formatDuration(item.durationSec)
            subtitle.text = if (dur.isNotBlank()) "$when_ • $dur" else when_.toString()
            unread.visibility = if (item.isRead) View.GONE else View.VISIBLE
            play.setImageResource(
                if (item.id == playingId) R.drawable.ic_pause else R.drawable.ic_play
            )
            play.isEnabled = item.hasContent
            play.alpha = if (item.hasContent) 1f else 0.4f

            play.setOnClickListener { onPlay(item) }
            callBtn.setOnClickListener { onCall(item) }
            delete.setOnClickListener { onDelete(item) }
        }

        private fun formatDuration(sec: Long): String {
            if (sec <= 0) return ""
            return String.format("%d:%02d", sec / 60, sec % 60)
        }
    }
}
