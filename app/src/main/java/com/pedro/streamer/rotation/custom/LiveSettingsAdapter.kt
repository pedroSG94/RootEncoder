package com.pedro.streamer.rotation.custom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pedro.streamer.R
import com.pedro.streamer.rotation.bean.IconInfo
import com.pedro.streamer.utils.Logger
import android.widget.ImageView
import com.pedro.streamer.rotation.annotation.IconState

class LiveSettingsAdapter(
    private val onIconClick: (IconInfo) -> Unit
): ListAdapter<IconInfo, LiveSettingsAdapter.IconVH>(DIFF) {
    companion object {
        private const val TAG = "LiveSettingsAdapter"
        private val DIFF = object : DiffUtil.ItemCallback<IconInfo>() {
            override fun areItemsTheSame(
                oldItem: IconInfo,
                newItem: IconInfo
            ): Boolean {
                return oldItem.type == newItem.type && oldItem.state == newItem.state
            }

            override fun areContentsTheSame(
                oldItem: IconInfo,
                newItem: IconInfo
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class IconVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView = itemView.findViewById<ImageView>(R.id.item_live_setting_icon)

        fun bind(icon: IconInfo) {
            Logger.d(TAG, "bind: icon = $icon")
            imageView.setImageResource(icon.images[icon.state])
            imageView.setOnClickListener {
                icon.state = if(icon.state == IconState.ON) IconState.OFF else IconState.ON
                imageView.setImageResource(icon.images[icon.state])
                onIconClick(icon)
            }
        }
        fun onRecycled() {
            Logger.d(TAG, "onRecycled: hashcode = ${hashCode()}")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_live_setting, parent, false)
        return IconVH(v)
    }

    override fun onBindViewHolder(holder: IconVH, position: Int) {
        Logger.d(TAG, "onBindViewHolder: position = $position")
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: IconVH) {
        super.onViewRecycled(holder)
        Logger.d(TAG, "onViewRecycled: hashcode = ${holder.hashCode()}")
        holder.onRecycled()
    }
}