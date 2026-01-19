package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cookbook.app.R
import com.cookbook.app.databinding.ItemImageEditBinding
import com.cookbook.app.util.ImageUtils

/**
 * Adapter for displaying and managing images in the recipe edit screen
 */
class EditImageAdapter(
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<EditImageAdapter.ImageViewHolder>() {

    private val images = mutableListOf<String>()

    fun submitList(newImages: List<String>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    fun addImage(imageDataUrl: String) {
        images.add(imageDataUrl)
        notifyItemInserted(images.size - 1)
    }

    fun removeImage(position: Int) {
        if (position in images.indices) {
            images.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, images.size)
        }
    }

    fun getImages(): List<String> = images.toList()

    override fun getItemCount(): Int = images.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageEditBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
    }

    class ImageViewHolder(
        private val binding: ItemImageEditBinding,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUrl: String) {
            // Load image - handle both base64 and regular URLs
            ImageUtils.loadImage(binding.ivImage, imageUrl, R.drawable.placeholder_recipe)
            
            binding.btnDelete.setOnClickListener {
                onDeleteClick(adapterPosition)
            }
        }
    }
}
