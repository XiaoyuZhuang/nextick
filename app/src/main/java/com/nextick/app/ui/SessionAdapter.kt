package com.nextick.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nextick.app.R
import com.nextick.app.core.Format
import com.nextick.app.data.Session
import com.nextick.app.data.TagNames
import com.nextick.app.databinding.ItemSessionBinding

class SessionAdapter : RecyclerView.Adapter<SessionAdapter.VH>() {
    private val items = ArrayList<Session>()
    fun submit(list: List<Session>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    class VH(val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.itemView.context; val s = items[position]
        holder.binding.timeRange.text = Format.range(s.startAt, s.endAt)
        holder.binding.tagName.text = TagNames.of(ctx, s.tagKey, s.tagName)
        holder.binding.duration.text = if (s.completed) Format.minutes(ctx, s.minutes) else ctx.getString(R.string.duration_early, Format.minutes(ctx, s.minutes))
        holder.binding.colorDot.background = circle(s.tagColor)
        val p = s.points
        holder.binding.points.text = p?.let { Format.points(it) } ?: "-"
        holder.binding.points.setTextColor(ContextCompat.getColor(ctx, if ((p ?: 0.0) >= 0) R.color.positive else R.color.negative))
    }
}
