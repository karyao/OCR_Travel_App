package com.karen_yao.chinesetravel.features.home.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.databinding.ItemSnapBinding

sealed interface SnapItemAction {
    data class Delete(val snap: PlaceSnap) : SnapItemAction
    data class OpenMap(val url: String) : SnapItemAction
}

class SnapsAdapter(
    private val onAction: (SnapItemAction) -> Unit
) : ListAdapter<PlaceSnap, SnapsViewHolder>(DIFF_CALLBACK) {

    var actionsEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_ACTION_STATE)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnapsViewHolder {
        val binding = ItemSnapBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SnapsViewHolder(binding, onAction)
    }

    override fun onBindViewHolder(holder: SnapsViewHolder, position: Int) {
        holder.bind(getItem(position), actionsEnabled)
    }

    override fun onBindViewHolder(
        holder: SnapsViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_ACTION_STATE)) {
            holder.setActionsEnabled(actionsEnabled)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    companion object {
        private const val PAYLOAD_ACTION_STATE = "action_state"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PlaceSnap>() {
            override fun areItemsTheSame(oldItem: PlaceSnap, newItem: PlaceSnap): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PlaceSnap, newItem: PlaceSnap): Boolean =
                oldItem == newItem
        }
    }
}

class SnapsViewHolder(
    private val binding: ItemSnapBinding,
    private val onAction: (SnapItemAction) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    private var hasMapAction = false

    fun bind(snap: PlaceSnap, actionsEnabled: Boolean) = with(binding) {
        btnDelete.setOnClickListener(null)
        tvGoogleMapsLink.setOnClickListener(null)
        locationRow.isVisible = false
        tvGoogleMapsLink.isVisible = false

        tvCn.text = snap.nameCn
        tvPinyin.text = snap.namePinyin
        tvTranslation.text = snap.translation

        val address = snap.address?.takeIf(String::isNotBlank)
        locationRow.isVisible = address != null
        tvAddress.text = address.orEmpty()

        val mapUrl = snap.googleMapsLink?.takeIf(::isValidMapUrl)
        hasMapAction = mapUrl != null
        tvGoogleMapsLink.isVisible = hasMapAction

        btnDelete.setOnClickListener { onAction(SnapItemAction.Delete(snap)) }
        tvGoogleMapsLink.setOnClickListener {
            mapUrl?.let { url -> onAction(SnapItemAction.OpenMap(url)) }
        }

        setActionsEnabled(actionsEnabled)
    }

    fun setActionsEnabled(enabled: Boolean) = with(binding) {
        btnDelete.isEnabled = enabled
        tvGoogleMapsLink.isEnabled = enabled && hasMapAction
    }

    private fun isValidMapUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val scheme = url.toUri().scheme?.lowercase()
        return scheme == "https" || scheme == "http" || scheme == "geo"
    }
}
