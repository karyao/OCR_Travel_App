package com.karen_yao.chinesetravel.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.data.model.PlaceSnap

class SnapsAdapter : ListAdapter<PlaceSnap, VH>(DIFF) {
    override fun onCreateViewHolder(p: ViewGroup, vType: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_snap, p, false)
        return VH(v)
    }
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PlaceSnap>() {
            override fun areItemsTheSame(a: PlaceSnap, b: PlaceSnap) = a.id == b.id
            override fun areContentsTheSame(a: PlaceSnap, b: PlaceSnap) = a == b
        }
    }
}

class VH(v: View) : RecyclerView.ViewHolder(v) {
    private val cn = v.findViewById<TextView>(R.id.tvCn)
    private val py = v.findViewById<TextView>(R.id.tvPinyin)
    private val ad = v.findViewById<TextView>(R.id.tvAddress)
    private val tr = v.findViewById<TextView>(R.id.tvTranslation)
    fun bind(s: PlaceSnap) {
        cn.text = s.nameCn
        py.text = s.namePinyin
        ad.text = s.address ?: ""
        tr.text = s.translation
    }
}
