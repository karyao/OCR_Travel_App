package com.karen_yao.chinesetravel.shared.ui

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.karen_yao.chinesetravel.core.media.applyExifOrientation
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal sealed interface PreviewSource {
    data class Asset(val name: String) : PreviewSource
    data class Photo(val file: File) : PreviewSource
}

internal fun interface PreviewDecoder {
    /** The caller supplies a background dispatcher and owns the returned bitmap. */
    suspend fun decode(source: PreviewSource, size: PreviewSize, scale: PreviewScale): Bitmap
}

internal class BitmapPreviewDecoder(private val assets: AssetManager) : PreviewDecoder {
    override suspend fun decode(source: PreviewSource, size: PreviewSize, scale: PreviewScale): Bitmap {
        currentCoroutineContext().ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid preview image bounds")
        currentCoroutineContext().ensureActive()
        val orientation = open(source).use {
            runCatching {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
        }
        val swapsAxes = orientation in setOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE
        )
        val uprightSize = if (swapsAxes) PreviewSize(bounds.outHeight, bounds.outWidth)
            else PreviewSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = previewSampleSize(uprightSize, size, scale)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        var owned: Bitmap? = null
        try {
            currentCoroutineContext().ensureActive()
            val decoded = open(source).use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("Could not decode preview image")
            owned = decoded
            currentCoroutineContext().ensureActive()
            val oriented = applyExifOrientation(decoded, orientation)
            if (oriented !== decoded) {
                owned = oriented
                decoded.recycle()
            }
            currentCoroutineContext().ensureActive()
            val transform = previewTransform(PreviewSize(oriented.width, oriented.height), size, scale)
            val matrix = Matrix().apply {
                setScale(
                    transform.output.width.toFloat() / transform.cropWidth,
                    transform.output.height.toFloat() / transform.cropHeight
                )
            }
            val preview = Bitmap.createBitmap(
                oriented, transform.left, transform.top, transform.cropWidth, transform.cropHeight, matrix, true
            )
            if (preview !== oriented) {
                owned = preview
                oriented.recycle()
            }
            currentCoroutineContext().ensureActive()
            owned = null
            return preview
        } finally {
            owned?.recycle()
        }
    }

    private fun open(source: PreviewSource): InputStream = when (source) {
        is PreviewSource.Asset -> assets.open(source.name)
        is PreviewSource.Photo -> source.file.inputStream()
    }
}
