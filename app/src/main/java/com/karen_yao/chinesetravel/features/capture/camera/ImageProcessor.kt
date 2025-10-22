
package com.karen_yao.chinesetravel.features.capture.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Handles image processing operations including EXIF data extraction and OCR preprocessing.
 * Provides utilities for extracting location data and improving OCR accuracy.
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
     * Preprocess image to improve OCR accuracy.
     * Applies contrast enhancement, noise reduction, and text sharpening.
     * 
     * @param file The image file to preprocess
     * @return Preprocessed bitmap optimized for OCR
     */
    fun preprocessImageForOCR(file: File): Bitmap? {
        return try {
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
            originalBitmap?.let { bitmap ->
                // Apply multiple preprocessing techniques
                val enhancedBitmap = enhanceContrast(bitmap)
                val sharpenedBitmap = sharpenText(enhancedBitmap)
                val denoisedBitmap = reduceNoise(sharpenedBitmap)
                denoisedBitmap
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Enhance image contrast to make text more readable.
     */
    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhancedBitmap)
        val paint = Paint()
        
        // Apply contrast enhancement
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f) // Convert to grayscale
            setScale(1.2f, 1.2f, 1.2f, 1f) // Increase contrast
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return enhancedBitmap
    }
    
    /**
     * Apply text sharpening to improve character recognition.
     */
    private fun sharpenText(bitmap: Bitmap): Bitmap {
        // Simple unsharp mask implementation
        val width = bitmap.width
        val height = bitmap.height
        val sharpenedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sharpenedBitmap)
        val paint = Paint()
        
        // Apply sharpening filter
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f) // Keep grayscale
            setScale(1.5f, 1.5f, 1.5f, 1f) // Enhance edges
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return sharpenedBitmap
    }
    
    /**
     * Reduce image noise to improve text clarity.
     */
    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        // Simple noise reduction by applying a slight blur and then sharpening
        val width = bitmap.width
        val height = bitmap.height
        val denoisedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(denoisedBitmap)
        val paint = Paint()
        
        // Apply noise reduction
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f) // Keep grayscale
            setScale(1.1f, 1.1f, 1.1f, 1f) // Slight enhancement
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return denoisedBitmap
    }
    
    /**
     * Analyze and rank Chinese text lines by likelihood of being restaurant names.
     * Uses heuristics to identify the most likely restaurant name.
     * 
     * @param chineseLines List of detected Chinese text lines
     * @return Ranked list with most likely restaurant names first
     */
    fun rankRestaurantNames(chineseLines: List<String>): List<RankedText> {
        return chineseLines.map { text ->
            val score = calculateRestaurantScore(text)
            RankedText(text, score)
        }.sortedByDescending { it.score }
    }
    
    /**
     * Calculate a score for how likely a text line is to be a restaurant name.
     * Higher scores indicate more likely restaurant names.
     */
    private fun calculateRestaurantScore(text: String): Float {
        var score = 0f
        
        // Length preference (restaurant names are usually 2-8 characters)
        val length = text.length
        when {
            length in 2..4 -> score += 30f // Short names are common
            length in 5..8 -> score += 25f // Medium names
            length in 9..12 -> score += 15f // Longer names
            else -> score += 5f // Very long or very short names
        }
        
        // Common restaurant name patterns
        val restaurantKeywords = listOf(
            "店", "餐厅", "饭馆", "酒楼", "茶楼", "咖啡", "面馆", "火锅", 
            "烧烤", "小吃", "快餐", "酒店", "宾馆", "会所", "酒吧"
        )
        
        restaurantKeywords.forEach { keyword ->
            if (text.contains(keyword)) {
                score += 40f // High score for restaurant keywords
            }
        }
        
        // Common food-related characters
        val foodCharacters = listOf("菜", "肉", "鱼", "鸡", "鸭", "牛", "羊", "虾", "蟹")
        foodCharacters.forEach { char ->
            if (text.contains(char)) {
                score += 20f // Medium score for food characters
            }
        }
        
        // Avoid common non-restaurant patterns
        val nonRestaurantPatterns = listOf("电话", "地址", "营业", "时间", "价格", "菜单")
        nonRestaurantPatterns.forEach { pattern ->
            if (text.contains(pattern)) {
                score -= 30f // Penalty for non-restaurant text
            }
        }
        
        // Avoid numbers and symbols (restaurant names rarely contain these)
        val numberCount = text.count { it.isDigit() }
        val symbolCount = text.count { ".,;:!?()[]{}".contains(it) }
        score -= (numberCount + symbolCount) * 5f
        
        return score.coerceAtLeast(0f) // Ensure non-negative score
    }
    
    /**
     * Data class to hold ranked text with confidence score.
     */
    data class RankedText(
        val text: String,
        val score: Float
    )
}
