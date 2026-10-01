package com.karen_yao.chinesetravel.shared.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.karen_yao.chinesetravel.core.media.applyExifOrientation
import java.io.File

/** Display-only image loading; OCR processing does not know about UI widgets. */
internal class ImagePreviewLoader {
    fun loadFile(view: ImageView, file: File) {
        try {
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
                )
            }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
            display(view, BitmapFactory.decodeFile(file.absolutePath), orientation)
        } catch (error: Exception) {
            failed(view, error)
        }
    }

    fun loadAsset(view: ImageView, assetName: String) {
        try {
            val assets = view.context.applicationContext.assets
            val orientation = runCatching {
                assets.open(assetName).use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
                    )
                }
            }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
            val bitmap = assets.open(assetName).use { BitmapFactory.decodeStream(it) }
            display(view, bitmap, orientation)
        } catch (error: Exception) {
            failed(view, error)
        }
    }

    private fun display(view: ImageView, original: Bitmap?, orientation: Int) {
        if (original == null) {
            view.setImageResource(android.R.drawable.ic_menu_camera)
            return
        }
        val oriented = try {
            applyExifOrientation(original, orientation)
        } catch (error: Exception) {
            original.recycle()
            throw error
        }
        if (oriented !== original) original.recycle()
        view.setImageBitmap(oriented)
    }

    private fun failed(view: ImageView, error: Exception) {
        Log.w("ImagePreviewLoader", "Could not load image preview", error)
        view.setImageResource(android.R.drawable.ic_menu_camera)
    }
}
