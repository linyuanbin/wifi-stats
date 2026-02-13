package com.lin.wifistats

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StatsAdapter(
    private var items: List<StatItem> = emptyList(),
    var onRowLongClick: ((StatItem) -> Unit)? = null
) : RecyclerView.Adapter<StatsAdapter.VH>() {

    fun setData(list: List<StatItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_stat_row, parent, false)
        return VH(v as android.view.ViewGroup)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.period.text = item.period
        holder.duration.text = item.durationFormatted()
        holder.upload.text = item.bytesFormatted(item.txBytes)
        holder.download.text = item.bytesFormatted(item.rxBytes)
        holder.itemView.setOnLongClickListener {
            onRowLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(root: android.view.ViewGroup) : RecyclerView.ViewHolder(root) {
        val period: TextView = root.findViewById(R.id.period)
        val duration: TextView = root.findViewById(R.id.duration)
        val upload: TextView = root.findViewById(R.id.upload)
        val download: TextView = root.findViewById(R.id.download)
    }
}
