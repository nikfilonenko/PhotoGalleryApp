package com.example.photogalleryapp.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.photogalleryapp.R


class MediaAdapter(
    private val onItemClick: (Boolean, Uri) -> Unit,
    private val onDeleteClick: (Boolean, Uri) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    private var mediaList: List<Media> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ITEM_TYPE_IMAGE ->
                ImageViewHolder(inflater.inflate(R.layout.item_picture, parent, false))
            ITEM_TYPE_VIDEO ->
                VideoViewHolder(inflater.inflate(R.layout.item_video, parent, false))
            else ->
                throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(mediaList[position])
    }

    override fun getItemCount(): Int {
        return mediaList.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (mediaList[position].isVideo) ITEM_TYPE_VIDEO else ITEM_TYPE_IMAGE
    }

    fun submitList(list: List<Media>) {
        mediaList = list
        notifyDataSetChanged()
    }

    fun deleteImage(currentPage: Int) {
        if (currentPage in 0 until itemCount) {
            val media = mediaList[currentPage]
            val allMedia = mediaList.toMutableList()
            allMedia.removeAt(currentPage)
            submitList(allMedia)
            onDeleteClick(allMedia.isEmpty(), media.uri)
        }
    }

    abstract class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: Media)
    }

    inner class ImageViewHolder(itemView: View) : MediaViewHolder(itemView) {
        private val imagePreview: ImageView = itemView.findViewById(R.id.imagePreview)

        override fun bind(item: Media) {
            imagePreview.load(item.uri)
            imagePreview.setOnClickListener { onItemClick(item.isVideo, item.uri) }
        }
    }

    inner class VideoViewHolder(itemView: View) : MediaViewHolder(itemView) {
        private val videoView: VideoView = itemView.findViewById(R.id.videoPreview)

        override fun bind(item: Media) {
            videoView.setVideoURI(item.uri)
            videoView.setOnClickListener { onItemClick(item.isVideo, item.uri) }
        }
    }

    companion object {
        private const val ITEM_TYPE_IMAGE = 1
        private const val ITEM_TYPE_VIDEO = 2
    }
}
