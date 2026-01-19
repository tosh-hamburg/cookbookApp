package com.cookbook.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.exifinterface.media.ExifInterface
import coil.load
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Utility functions for image handling
 */
object ImageUtils {
    
    private const val TAG = "ImageUtils"
    private const val DATA_URL_PREFIX = "data:image"
    private const val MAX_IMAGE_SIZE = 1024 // Max dimension for uploaded images
    private const val JPEG_QUALITY = 80 // JPEG compression quality
    private const val MAX_BASE64_SIZE_BYTES = 5 * 1024 * 1024 // 5MB max for base64 string
    
    /**
     * Check if the string is a base64 data URL
     */
    fun isBase64DataUrl(url: String?): Boolean {
        return url?.startsWith(DATA_URL_PREFIX) == true
    }
    
    /**
     * Load an image into an ImageView, handling both base64 data URLs and regular URLs
     * 
     * @param imageView The ImageView to load the image into
     * @param imageUrl The URL or base64 data URL of the image
     * @param placeholderRes Optional placeholder drawable resource
     */
    fun loadImage(imageView: ImageView, imageUrl: String?, @DrawableRes placeholderRes: Int? = null) {
        if (imageUrl.isNullOrEmpty()) {
            if (placeholderRes != null) {
                imageView.setImageResource(placeholderRes)
            }
            return
        }
        
        if (isBase64DataUrl(imageUrl)) {
            // Decode base64 and set directly
            val bitmap = decodeBase64Image(imageUrl)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else if (placeholderRes != null) {
                imageView.setImageResource(placeholderRes)
            }
        } else {
            // Use Coil for regular URLs
            imageView.load(imageUrl) {
                if (placeholderRes != null) {
                    placeholder(placeholderRes)
                    error(placeholderRes)
                }
                crossfade(true)
            }
        }
    }
    
    /**
     * Decode a base64 data URL to a Bitmap
     * 
     * @param dataUrl The data URL in format "data:image/jpeg;base64,..."
     * @return Decoded Bitmap or null if decoding fails
     */
    fun decodeBase64Image(dataUrl: String): Bitmap? {
        return try {
            // Extract base64 data after the comma
            val base64Data = dataUrl.substringAfter(",")
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            
            // Decode with sampling to reduce memory usage for large images
            val options = BitmapFactory.Options().apply {
                // First, just get the dimensions
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
            
            // Calculate sample size for reasonable display size (max 512px)
            val maxSize = 512
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxSize || options.outHeight / sampleSize > maxSize) {
                sampleSize *= 2
            }
            
            // Now decode with the calculated sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 image", e)
            null
        }
    }
    
    /**
     * Load an image from a URI and convert it to a base64 data URL
     * Handles rotation based on EXIF data
     * Ensures the result is under 5MB
     * 
     * @param context The context
     * @param uri The URI of the image
     * @return Base64 data URL or null if loading fails
     */
    fun uriToBase64DataUrl(context: Context, uri: Uri): String? {
        return try {
            // Load bitmap with appropriate sampling
            val bitmap = loadBitmapFromUri(context, uri) ?: return null
            
            // Rotate if needed based on EXIF
            val rotatedBitmap = rotateBitmapIfRequired(context, uri, bitmap)
            
            // Resize if too large
            var resizedBitmap = resizeBitmap(rotatedBitmap, MAX_IMAGE_SIZE)
            
            // Convert to base64 with progressive quality reduction if needed
            var quality = JPEG_QUALITY
            var base64Result = bitmapToBase64DataUrlWithQuality(resizedBitmap, quality)
            
            // If still too large, reduce quality progressively
            while (base64Result.length > MAX_BASE64_SIZE_BYTES && quality > 30) {
                quality -= 10
                Log.d(TAG, "Image too large (${base64Result.length / 1024}KB), reducing quality to $quality")
                base64Result = bitmapToBase64DataUrlWithQuality(resizedBitmap, quality)
            }
            
            // If still too large, also reduce dimensions
            var currentMaxSize = MAX_IMAGE_SIZE
            while (base64Result.length > MAX_BASE64_SIZE_BYTES && currentMaxSize > 400) {
                currentMaxSize -= 200
                Log.d(TAG, "Image still too large, reducing dimensions to $currentMaxSize")
                resizedBitmap = resizeBitmap(rotatedBitmap, currentMaxSize)
                base64Result = bitmapToBase64DataUrlWithQuality(resizedBitmap, quality)
            }
            
            Log.d(TAG, "Final image size: ${base64Result.length / 1024}KB, quality=$quality, maxDim=$currentMaxSize")
            base64Result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert URI to base64", e)
            null
        }
    }
    
    /**
     * Convert a Bitmap to a base64 data URL with default quality
     */
    fun bitmapToBase64DataUrl(bitmap: Bitmap): String {
        return bitmapToBase64DataUrlWithQuality(bitmap, JPEG_QUALITY)
    }
    
    /**
     * Convert a Bitmap to a base64 data URL with specified quality
     */
    private fun bitmapToBase64DataUrlWithQuality(bitmap: Bitmap, quality: Int): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
    
    /**
     * Load a bitmap from URI with appropriate sampling to avoid OutOfMemoryError
     */
    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            // First, get the dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            // Calculate sample size
            var sampleSize = 1
            while (options.outWidth / sampleSize > MAX_IMAGE_SIZE * 2 || 
                   options.outHeight / sampleSize > MAX_IMAGE_SIZE * 2) {
                sampleSize *= 2
            }
            
            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI", e)
            null
        }
    }
    
    /**
     * Rotate bitmap based on EXIF orientation data
     */
    private fun rotateBitmapIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                
                val rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                
                if (rotationDegrees != 0f) {
                    val matrix = Matrix().apply {
                        postRotate(rotationDegrees)
                    }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read EXIF data", e)
            bitmap
        }
    }
    
    /**
     * Resize bitmap to fit within maxSize while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
