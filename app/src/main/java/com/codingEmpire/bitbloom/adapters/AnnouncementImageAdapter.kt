// AnnouncementImageAdapter.kt
package com.codingEmpire.bitbloom.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codingEmpire.bitbloom.databinding.ItemImageAnnouncementBinding

class AnnouncementImageAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<AnnouncementImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val binding: ItemImageAnnouncementBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemImageAnnouncementBinding.inflate(inflater, parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = images[position]
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .into(holder.binding.imageItem)
    }

    override fun getItemCount(): Int = images.size
}
