package com.cookbook.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cookbook.app.R
import com.cookbook.app.databinding.ItemImagePagerBinding
import com.cookbook.app.util.ImageUtils

/**
 * Adapter for image carousel in recipe detail
 */
class ImagePagerAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImagePagerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
    }
    
    override fun getItemCount(): Int = images.size
    
    class ImageViewHolder(
        private val binding: ItemImagePagerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(imageUrl: String) {
            if (ImageUtils.isBase64DataUrl(imageUrl)) {
                // Handle base64 data URL
                val bitmap = ImageUtils.decodeBase64Image(imageUrl)
                if (bitmap != null) {
                    binding.ivImage.setImageBitmap(bitmap)
                } else {
                    binding.ivImage.setImageResource(R.drawable.placeholder_recipe)
                }
            } else {
                // Handle regular URL with Coil
                binding.ivImage.load(imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_recipe)
                    error(R.drawable.placeholder_recipe)
                }
            }
        }
    }
}
