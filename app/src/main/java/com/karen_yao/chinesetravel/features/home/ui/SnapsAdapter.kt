package com.karen_yao.chinesetravel.features.home.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap

/**
 * RecyclerView adapter for displaying captured place snaps.
 * Uses ListAdapter for efficient list updates.
 */
class SnapsAdapter(
    private val onDeleteClick: (PlaceSnap) -> Unit
) : ListAdapter<PlaceSnap, SnapsViewHolder>(DIFF_CALLBACK) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnapsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_snap, parent, false)
        return SnapsViewHolder(view, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: SnapsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PlaceSnap>() {
            override fun areItemsTheSame(oldItem: PlaceSnap, newItem: PlaceSnap) = 
                oldItem.id == newItem.id
                
            override fun areContentsTheSame(oldItem: PlaceSnap, newItem: PlaceSnap) = 
                oldItem == newItem
        }
    }
}

/**
 * ViewHolder for individual snap items in the RecyclerView.
 */
class SnapsViewHolder(
    view: View,
    private val onDeleteClick: (PlaceSnap) -> Unit
) : RecyclerView.ViewHolder(view) {
    private val chineseText = view.findViewById<TextView>(R.id.tvCn)
    private val pinyinText = view.findViewById<TextView>(R.id.tvPinyin)
    private val addressText = view.findViewById<TextView>(R.id.tvAddress)
    private val translationText = view.findViewById<TextView>(R.id.tvTranslation)
    private val googleMapsLinkText = view.findViewById<TextView>(R.id.tvGoogleMapsLink)
    private val deleteButton = view.findViewById<Button>(R.id.btnDelete)
    
    fun bind(snap: PlaceSnap) {
        chineseText.text = snap.nameCn
        pinyinText.text = snap.namePinyin
        addressText.text = snap.address ?: ""
        translationText.text = snap.translation
        
        // Make Google Maps link clickable
        if (!snap.googleMapsLink.isNullOrBlank()) {
            googleMapsLinkText.text = "🗺️ Open in Google Maps"
            googleMapsLinkText.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(snap.googleMapsLink))
                itemView.context.startActivity(intent)
            }
        } else {
            googleMapsLinkText.text = "No location data"
            googleMapsLinkText.setOnClickListener(null)
        }
        
        // Set up delete button
        deleteButton.setOnClickListener {
            onDeleteClick(snap)
        }
    }
}
