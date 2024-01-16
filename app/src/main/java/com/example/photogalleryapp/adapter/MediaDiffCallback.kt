package com.example.photogalleryapp.adapter

import androidx.recyclerview.widget.DiffUtil
import com.example.photogalleryapp.adapter.Media

class MediaDiffCallback : DiffUtil.ItemCallback<Media>() {
    override fun areItemsTheSame(oldItem: Media, newItem: Media): Boolean = oldItem.uri == newItem.uri

    override fun areContentsTheSame(oldItem: Media, newItem: Media): Boolean = oldItem == newItem
}
