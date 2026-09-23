package com.nextick.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nextick.app.R
import com.nextick.app.core.Format
import com.nextick.app.core.Notifier
import com.nextick.app.data.Session
import com.nextick.app.data.TagNames
import com.nextick.app.databinding.ItemSessionBinding

class SessionAdapter : RecyclerView.Adapter<SessionAdapter.VH>() {
    private val items = ArrayList<Session>()

    fun submit(list: List<Session>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            ItemSessionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.itemView.context
        val session = items[position]

        holder.itemView.setOnClickListener {
            Notifier.tap(ctx)
        }

        holder.binding.timeRange.text =
            Format.range(session.startAt, session.endAt)

        holder.binding.tagName.text =
            TagNames.of(ctx, session.tagKey, session.tagName)

        holder.binding.duration.text =
            if (session.completed) {
                Format.minutes(ctx, session.minutes)
            } else {
                ctx.getString(
                    R.string.duration_early,
                    Format.minutes(ctx, session.minutes)
                )
            }

        holder.binding.colorDot.background = circle(session.tagColor)

        val points = session.points
        holder.binding.points.text =
            points?.let { Format.points(it) } ?: "-"

        holder.binding.points.setTextColor(
            ContextCompat.getColor(
                ctx,
                if ((points ?: 0.0) >= 0) R.color.positive else R.color.negative
            )
        )
    }
}
