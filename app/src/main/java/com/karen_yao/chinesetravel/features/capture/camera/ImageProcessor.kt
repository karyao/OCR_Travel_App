
package com.karen_yao.chinesetravel.features.capture.camera

import androidx.exifinterface.media.ExifInterface
import java.io.File

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
}
