
package com.karen_yao.chinesetravel.features.capture.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Handles image processing operations including EXIF data extraction.
 * Provides utilities for extracting location data from captured images.
 */
class ImageProcessor {
    
    /**
     * Extract location coordinates from image EXIF data.
     * 
     * @param file The image file to process
     * @return Pair of (latitude, longitude) or null if no location data found
     */
    fun extractLocationFromFile(file: File): Pair<Double, Double>? {
        return try {
            val exif = ExifInterface(file.absolutePath)
            exif.latLong?.let { coordinates ->
                Pair(coordinates[0], coordinates[1])
            }
        } catch (exception: Exception) {
            null
        }
    }
    
    /**
     * Check if an image file has location data in EXIF.
     * 
     * @param file The image file to check
     * @return True if location data is present, false otherwise
     */
    fun hasLocationData(file: File): Boolean {
        return extractLocationFromFile(file) != null
    }
    
    /**
     * Preprocess image for better OCR accuracy.
     * Applies contrast enhancement and grayscale conversion.
     * 
     * @param file The original image file
     * @return Preprocessed image file optimized for OCR
     */
    fun preprocessImageForOCR(file: File): File {
        return try {
            // Load original bitmap
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return file // Return original if can't decode
            
            // Apply preprocessing
            val preprocessedBitmap = enhanceImageForOCR(originalBitmap)
            
            // Save preprocessed image
            val preprocessedFile = File(file.parent, "preprocessed_${file.name}")
            FileOutputStream(preprocessedFile).use { out ->
                preprocessedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Recycle bitmaps to free memory
            originalBitmap.recycle()
            preprocessedBitmap.recycle()
            
            preprocessedFile
        } catch (e: Exception) {
            // Return original file if preprocessing fails
            file
        }
    }
    
    /**
     * Enhance image for better OCR accuracy.
     * Applies grayscale conversion and contrast enhancement.
     * 
     * @param bitmap The original bitmap
     * @return Enhanced bitmap optimized for OCR
     */
    private fun enhanceImageForOCR(bitmap: Bitmap): Bitmap {
        // Create a new bitmap with the same dimensions
        val enhancedBitmap = Bitmap.createBitmap(
            bitmap.width, 
            bitmap.height, 
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(enhancedBitmap)
        val paint = Paint()
        
        // Apply grayscale and contrast enhancement
        val colorMatrix = ColorMatrix()
        
        // Convert to grayscale
        colorMatrix.setSaturation(0f)
        
        // Enhance contrast (increase contrast by 1.5x)
        val contrast = 1.5f
        val translate = (-128f * contrast + 128f)
        colorMatrix.set(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return enhancedBitmap
    }
    
    /**
     * Check if image needs preprocessing based on quality indicators.
     * 
     * @param file The image file to check
     * @return True if preprocessing is recommended
     */
    fun shouldPreprocessImage(file: File): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) return false
            
            // Check image dimensions (very small images might benefit from preprocessing)
            val width = bitmap.width
            val height = bitmap.height
            
            // Check if image is too small (less than 800px in either dimension)
            val isSmall = width < 800 || height < 800
            
            // Check if image is very large (might be too detailed)
            val isLarge = width > 3000 || height > 3000
            
            bitmap.recycle()
            
            // Recommend preprocessing for small images or very large images
            isSmall || isLarge
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Load and display an image with proper rotation handling.
     * Respects EXIF orientation data to display images correctly.
     * 
     * @param imageView The ImageView to display the image in
     * @param imagePath Path to the image file
     */
    fun loadImageWithRotation(imageView: android.widget.ImageView, imagePath: String) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                android.util.Log.w("ImageProcessor", "Image file does not exist: $imagePath")
                return
            }
            
            // Get EXIF orientation
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            
            // Decode the bitmap
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                android.util.Log.w("ImageProcessor", "Failed to decode image: $imagePath")
                return
            }
            
            // Apply rotation based on EXIF orientation
            val rotatedBitmap = rotateBitmap(bitmap, orientation)
            
            // Set the rotated bitmap to the ImageView
            imageView.setImageBitmap(rotatedBitmap)
            
            // Recycle the original bitmap if we created a rotated version
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ImageProcessor", "Error loading image with rotation: ${e.message}")
            // Fallback to regular loading
            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                imageView.setImageBitmap(bitmap)
            } catch (fallbackException: Exception) {
                android.util.Log.e("ImageProcessor", "Fallback image loading also failed: ${fallbackException.message}")
            }
        }
    }
    
    /**
     * Load and display an image from assets with proper rotation handling.
     * 
     * @param imageView The ImageView to display the image in
     * @param context Context for accessing assets
     * @param assetName Name of the asset file
     */
    fun loadImageFromAssetsWithRotation(imageView: android.widget.ImageView, context: android.content.Context, assetName: String) {
        try {
            val inputStream = context.assets.open(assetName)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageProcessor", "Error loading asset image: ${e.message}")
        }
    }
    
    /**
     * Rotate bitmap based on EXIF orientation.
     * 
     * @param bitmap Original bitmap
     * @param orientation EXIF orientation value
     * @return Rotated bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipImage(bitmap, horizontal = true, vertical = false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipImage(bitmap, horizontal = false, vertical = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                val rotated = rotateImage(bitmap, 90f)
                flipImage(rotated, horizontal = true, vertical = false)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val rotated = rotateImage(bitmap, 270f)
                flipImage(rotated, horizontal = true, vertical = false)
            }
            else -> bitmap // No rotation needed
        }
    }
    
    /**
     * Rotate image by specified degrees.
     */
    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Flip image horizontally or vertically.
     */
    private fun flipImage(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postScale(
            if (horizontal) -1f else 1f,
            if (vertical) -1f else 1f
        )
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
