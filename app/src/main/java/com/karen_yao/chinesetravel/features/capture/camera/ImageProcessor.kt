
package com.karen_yao.chinesetravel.features.capture.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

private const val MAX_OCR_LONG_EDGE = 2560
private const val MAX_OCR_PIXEL_COUNT = 4_000_000L

internal class EnhancedOcrImage(val bitmap: Bitmap) : Closeable {
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal data class OcrImageSize(val width: Int, val height: Int)

/** Preserve as much detail as the processing budget permits; never upscale. */
internal fun calculateOcrImageSize(width: Int, height: Int): OcrImageSize {
    if (width <= 0 || height <= 0) return OcrImageSize(width, height)
    val scale = minOf(
        1.0,
        MAX_OCR_LONG_EDGE.toDouble() / maxOf(width, height),
        kotlin.math.sqrt(MAX_OCR_PIXEL_COUNT.toDouble() / (width.toDouble() * height))
    )
    return OcrImageSize(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1)
    )
}

/** Decode at a power of two that still retains every pixel needed by the target. */
internal fun calculateOcrInSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    val target = calculateOcrImageSize(width, height)
    var sampleSize = 1
    while (sampleSize <= Int.MAX_VALUE / 2) {
        val next = sampleSize * 2
        if (width / next < target.width || height / next < target.height) break
        sampleSize = next
    }
    return sampleSize
}

/**
 * Handles image processing operations including EXIF data extraction.
 * Provides utilities for extracting location data from captured images.
 */
class ImageProcessor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    
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
     * The returned image owns its bitmap and must be closed by the caller.
     *
     * @param file The original image file
     * @return An upright, preprocessed bitmap optimized for OCR, or null when decoding fails
     */
    internal suspend fun preprocessBitmapForOcr(file: File): EnhancedOcrImage? {
        var originalBitmap: Bitmap? = null
        var enhancedBitmap: Bitmap? = null
        var orientedBitmap: Bitmap? = null

        return try {
            currentCoroutineContext().ensureActive()
            val orientation = withContext(ioDispatcher) {
                readOrientation(file)
            }

            originalBitmap = withContext(ioDispatcher) {
                decodeBoundedBitmap(file)
            } ?: return null

            currentCoroutineContext().ensureActive()
            enhancedBitmap = withContext(computationDispatcher) {
                enhanceImageForOCR(checkNotNull(originalBitmap))
            }
            originalBitmap.recycle()
            originalBitmap = null

            currentCoroutineContext().ensureActive()
            orientedBitmap = withContext(computationDispatcher) {
                rotateBitmap(checkNotNull(enhancedBitmap), orientation)
            }
            if (orientedBitmap !== enhancedBitmap) {
                enhancedBitmap.recycle()
            }
            enhancedBitmap = null

            currentCoroutineContext().ensureActive()
            EnhancedOcrImage(checkNotNull(orientedBitmap)).also {
                orientedBitmap = null
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        } finally {
            orientedBitmap?.takeIf { it !== enhancedBitmap }?.recycle()
            enhancedBitmap?.recycle()
            originalBitmap?.recycle()
        }
    }
    
    /**
     * Enhance image for better OCR accuracy.
     * Applies grayscale conversion and contrast enhancement.
     * 
     * @param bitmap The original bitmap
     * @return Enhanced bitmap optimized for OCR
     */
    internal fun enhanceImageForOCR(bitmap: Bitmap): Bitmap {
        // Create a new bitmap with the same dimensions
        val enhancedBitmap = Bitmap.createBitmap(
            bitmap.width, 
            bitmap.height, 
            Bitmap.Config.ARGB_8888
        )
        
        return try {
            val canvas = Canvas(enhancedBitmap)
            val paint = Paint()

            // Apply grayscale and contrast enhancement
            val grayscaleMatrix = ColorMatrix().apply {
                setSaturation(0f)
            }

            // Enhance contrast (increase contrast by 1.5x)
            val contrast = 1.5f
            val translate = (-128f * contrast + 128f)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))

            // postConcat applies the contrast matrix after grayscale conversion.
            grayscaleMatrix.postConcat(contrastMatrix)

            paint.colorFilter = ColorMatrixColorFilter(grayscaleMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            enhancedBitmap
        } catch (exception: Exception) {
            enhancedBitmap.recycle()
            throw exception
        }
    }

    private fun readOrientation(file: File): Int =
        runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

    private fun decodeBoundedBitmap(file: File): Bitmap? {
        val bounds = readImageBounds(file) ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateOcrInSampleSize(bounds.width, bounds.height)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val target = calculateOcrImageSize(bounds.width, bounds.height)
        return try {
            val resized = Bitmap.createScaledBitmap(decoded, target.width, target.height, true)
            if (resized !== decoded) decoded.recycle()
            resized
        } catch (exception: Exception) {
            decoded.recycle()
            throw exception
        }
    }

    private fun readImageBounds(file: File): ImageBounds? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            ImageBounds(options.outWidth, options.outHeight)
        } else {
            null
        }
    }
    
    /**
     * Check if image needs preprocessing based on quality indicators.
     * 
     * @param file The image file to check
     * @return True if preprocessing is recommended
     */
    fun shouldPreprocessImage(file: File): Boolean {
        return try {
            val bounds = readImageBounds(file) ?: return false

            // Check image dimensions (very small images might benefit from preprocessing)
            val width = bounds.width
            val height = bounds.height
            
            // Check if image is too small (less than 800px in either dimension)
            val isSmall = width < 800 || height < 800
            
            // Check if image is very large (might be too detailed)
            val isLarge = width > 3000 || height > 3000
            
            // Recommend preprocessing for small images or very large images
            isSmall || isLarge
        } catch (e: Exception) {
            false
        }
    }

    private data class ImageBounds(val width: Int, val height: Int)

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
            
            // Create a temporary file to read EXIF data
            val tempFile = File.createTempFile("temp_asset_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            // Get EXIF orientation from the temporary file
            val exif = ExifInterface(tempFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            
            // Decode the bitmap
            val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            if (bitmap != null) {
                // Apply rotation based on EXIF orientation
                val rotatedBitmap = rotateBitmap(bitmap, orientation)
                imageView.setImageBitmap(rotatedBitmap)
                
                // Recycle the original bitmap if we created a rotated version
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
            }
            
            // Clean up temporary file
            tempFile.delete()
            
        } catch (e: Exception) {
            android.util.Log.e("ImageProcessor", "Error loading asset image: ${e.message}")
            // Fallback to regular loading
            try {
                val inputStream = context.assets.open(assetName)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (fallbackException: Exception) {
                android.util.Log.e("ImageProcessor", "Fallback asset loading also failed: ${fallbackException.message}")
            }
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
                try {
                    flipImage(rotated, horizontal = true, vertical = false)
                } finally {
                    rotated.recycle()
                }
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val rotated = rotateImage(bitmap, 270f)
                try {
                    flipImage(rotated, horizontal = true, vertical = false)
                } finally {
                    rotated.recycle()
                }
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
