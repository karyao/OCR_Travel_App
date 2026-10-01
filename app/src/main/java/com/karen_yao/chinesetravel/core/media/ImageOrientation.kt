package com.karen_yao.chinesetravel.core.media

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface

internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
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
