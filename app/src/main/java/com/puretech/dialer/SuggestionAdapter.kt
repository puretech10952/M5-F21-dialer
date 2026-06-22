package com.puretech.dialer

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

/**
 * Shows ranked contact suggestions. Tap = call immediately; long-press = the
 * caller-provided options popup (edit / message / copy).
 */
class SuggestionAdapter(
    private val onCall: (Contact) -> Unit,
    private val onOptions: (Contact, View) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.VH>() {

    private val items = ArrayList<Contact>()

    fun submit(list: List<Contact>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ShapeableImageView = view.findViewById(R.id.photo)
        val name: TextView = view.findViewById(R.id.name)
        val number: TextView = view.findViewById(R.id.number)

        init {
            view.setOnClickListener {
                items.getOrNull(bindingAdapterPosition)?.let(onCall)
            }
            view.setOnLongClickListener {
                items.getOrNull(bindingAdapterPosition)?.let { onOptions(it, view) }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.name.text = c.name.ifBlank { c.number }
        holder.number.text = c.number

        if (c.photoUri != null) {
            // Real photo: clear the tint (otherwise it paints over the photo).
            holder.photo.imageTintList = null
            holder.photo.setPadding(0, 0, 0, 0)
            holder.photo.setImageURI(c.photoUri)
            if (holder.photo.drawable == null) applyFallbackAvatar(holder) // uri failed
        } else {
            applyFallbackAvatar(holder)
        }
    }

    private fun applyFallbackAvatar(holder: VH) {
        val pad = (8 * holder.itemView.resources.displayMetrics.density).toInt()
        holder.photo.setPadding(pad, pad, pad, pad)
        holder.photo.imageTintList = ColorStateList.valueOf(0xFF5F6368.toInt())
        holder.photo.setImageResource(R.drawable.ic_person)
    }

    override fun getItemCount(): Int = items.size
}
