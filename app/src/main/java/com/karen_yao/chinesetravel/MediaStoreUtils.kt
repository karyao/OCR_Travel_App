package com.karen_yao.chinesetravel.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

private const val TAG = "AssetExport"

// (A) export a single asset by name, e.g. "sample1.jpg" or "samples/menu1.jpg"
fun exportOneAssetToGallery(context: Context, assetName: String): Uri? {
    val mime = when {
        assetName.endsWith(".png", true) -> "image/png"
        assetName.endsWith(".jpg", true) || assetName.endsWith(".jpeg", true) -> "image/jpeg"
        else -> { Log.e(TAG, "Unsupported asset type: $assetName"); return null }
    }
    val cr = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, assetName.substringAfterLast('/'))
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ChineseTravel")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ChineseTravel")
            if (!dir.exists()) dir.mkdirs()
            put(MediaStore.Images.Media.DATA, File(dir, assetName.substringAfterLast('/')).absolutePath)
        }
    }
    val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        context.assets.open(assetName).use { ins ->
            cr.openOutputStream(uri)?.use { outs -> ins.copyTo(outs) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            cr.update(uri, values, null, null)
        }
        Log.d(TAG, "Exported $assetName -> $uri")
        uri
    } catch (e: Exception) {
        Log.e(TAG, "Failed writing $assetName", e)
        runCatching { cr.delete(uri, null, null) }
        null
    }
}

// (B) export all image assets under optional subdir ("" = root of assets)
fun exportAssetsToGallery(context: Context, subdir: String = ""): Int {
    val am = context.assets
    fun list(path: String): List<String> {
        val items = am.list(path) ?: return emptyList()
        val out = mutableListOf<String>()
        for (n in items) {
            val full = if (path.isEmpty()) n else "$path/$n"
            val kids = am.list(full)
            if (kids != null && kids.isNotEmpty()) out += list(full)
            else if (full.endsWith(".jpg", true) || full.endsWith(".jpeg", true) || full.endsWith(".png", true)) out += full
        }
        return out
    }
    val names = list(subdir)
    var ok = 0
    for (p in names) if (exportOneAssetToGallery(context, p) != null) ok++
    return ok
}
