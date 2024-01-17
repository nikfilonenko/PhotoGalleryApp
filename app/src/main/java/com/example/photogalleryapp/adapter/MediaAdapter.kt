package com.example.photogalleryapp.adapter

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.photogalleryapp.R
import com.example.photogalleryapp.utils.layoutInflater

class MediaAdapter(
    private val onItemClick: (Boolean, Uri) -> Unit,
    private val onDeleteClick: (Boolean, Uri) -> Unit
) : RecyclerView.Adapter<MediaAdapter.PicturesViewHolder>() {

    private var mediaList: List<Media> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PicturesViewHolder {
        return PicturesViewHolder(parent.context.layoutInflater.inflate(R.layout.item_picture, parent, false))
    }

    override fun onBindViewHolder(holder: PicturesViewHolder, position: Int) {
        holder.bind(mediaList[position])
    }

    override fun getItemCount(): Int {
        return mediaList.size
    }

    fun submitList(list: List<Media>) {
        mediaList = list
        notifyDataSetChanged()
    }

    fun deleteImage(currentPage: Int) {
        if (currentPage < itemCount) {
            val media = mediaList[currentPage]
            val allMedia = mediaList.toMutableList()
            allMedia.removeAt(currentPage)
            submitList(allMedia)
            onDeleteClick(allMedia.isEmpty(), media.uri)
        }
    }

    inner class PicturesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagePreview: ImageView = itemView.findViewById(R.id.imagePreview)
        private val imagePlay: ImageView = itemView.findViewById(R.id.imagePlay)

        fun bind(item: Media) {
            imagePlay.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            imagePreview.load(item.uri)
            imagePreview.setOnClickListener { onItemClick(item.isVideo, item.uri) }
        }
    }
}
