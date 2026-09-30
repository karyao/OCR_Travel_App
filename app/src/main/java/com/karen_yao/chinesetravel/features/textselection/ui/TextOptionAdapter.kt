package com.karen_yao.chinesetravel.features.textselection.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.karen_yao.chinesetravel.R

/** Selection is rendered from ViewModel state, never changed locally on click. */
class TextOptionAdapter(
    private val texts: List<String>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<TextOptionAdapter.TextOptionViewHolder>() {
    private var selectedIndex = -1
    private var selectionEnabled = true

    fun render(index: Int, enabled: Boolean) {
        if (selectedIndex == index && selectionEnabled == enabled) return
        val previous = selectedIndex
        val availabilityChanged = selectionEnabled != enabled
        selectedIndex = index
        selectionEnabled = enabled
        if (availabilityChanged) notifyItemRangeChanged(0, itemCount)
        else {
            if (previous in texts.indices) notifyItemChanged(previous)
            if (index in texts.indices) notifyItemChanged(index)
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextOptionViewHolder =
        TextOptionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_text_option, parent, false))
    override fun onBindViewHolder(holder: TextOptionViewHolder, position: Int) {
        holder.bind(texts[position], position == selectedIndex, selectionEnabled)
    }
    override fun getItemCount() = texts.size

    inner class TextOptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.tvTextOption)
        private val selectedView = view.findViewById<TextView>(R.id.tvSelected)
        init {
            itemView.setOnClickListener {
                val index = bindingAdapterPosition
                if (selectionEnabled && index != RecyclerView.NO_POSITION) onItemClick(index)
            }
        }
        fun bind(text: String, selected: Boolean, enabled: Boolean) {
            textView.text = text
            selectedView.visibility = if (selected) View.VISIBLE else View.GONE
            itemView.isEnabled = enabled
            itemView.isSelected = selected
        }
    }
}
